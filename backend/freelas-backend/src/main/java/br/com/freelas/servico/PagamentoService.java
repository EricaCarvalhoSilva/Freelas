package br.com.freelas.servico;

import br.com.freelas.config.PropriedadesFreelas;
import br.com.freelas.dto.PagamentoDto.*;
import br.com.freelas.dto.PaginaResposta;
import br.com.freelas.entidade.*;
import br.com.freelas.enums.*;
import br.com.freelas.excecao.*;
import br.com.freelas.fila.FilaService;
import br.com.freelas.integracao.GatewayPagamento;
import br.com.freelas.repositorio.*;
import br.com.freelas.seguranca.Hashes;
import br.com.freelas.seguranca.UsuarioAtual;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Pagamento em custódia (Fluxo de Integração do Pagamento, Luan):
 * PAGO_CUSTODIA (aceite do freelancer) -> AGUARDANDO_CONFIRMACAO (check-out, janela de 6 h)
 * -> LIBERADO (empresa confirma ou prazo vence) ou EM_DISPUTA -> LIBERADO / ESTORNADO.
 * Toda mudança de saldo trava a carteira com SELECT ... FOR UPDATE.
 */
@Service
public class PagamentoService {

    private static final Logger log = LoggerFactory.getLogger(PagamentoService.class);

    private final PagamentoRepository pagamentos;
    private final MeioPagamentoEmpresaRepository meiosPagamento;
    private final CarteiraRepository carteiras;
    private final MovimentacaoCarteiraRepository movimentacoes;
    private final ContratacaoRepository contratacoes;
    private final GatewayPagamento gateway;
    private final NotificacaoService notificacoes;
    private final AuditoriaService auditoria;
    private final FilaService fila;
    private final PropriedadesFreelas props;
    private final JsonMapper jsonMapper;

    public PagamentoService(PagamentoRepository pagamentos, MeioPagamentoEmpresaRepository meiosPagamento,
                            CarteiraRepository carteiras, MovimentacaoCarteiraRepository movimentacoes,
                            ContratacaoRepository contratacoes, GatewayPagamento gateway,
                            NotificacaoService notificacoes, AuditoriaService auditoria, FilaService fila,
                            PropriedadesFreelas props, JsonMapper jsonMapper) {
        this.pagamentos = pagamentos;
        this.meiosPagamento = meiosPagamento;
        this.carteiras = carteiras;
        this.movimentacoes = movimentacoes;
        this.contratacoes = contratacoes;
        this.gateway = gateway;
        this.notificacoes = notificacoes;
        this.auditoria = auditoria;
        this.fila = fila;
        this.props = props;
        this.jsonMapper = jsonMapper;
    }

    // ------------------------------------------------------------------ cobrança no aceite

    /**
     * Decisão do grupo: a empresa é cobrada (100%) quando o freelancer aceita.
     * Se já existe pagamento em custódia (novo aceite depois de uma edição), só ajusta o valor.
     */
    @Transactional
    public Pagamento garantirPagamento(Contratacao c) {
        var existente = pagamentos.findByContratacaoId(c.getId());
        if (existente.isPresent()) {
            Pagamento p = existente.get();
            if (p.getStatus() != StatusPagamento.PAGO_CUSTODIA) {
                throw new ConflitoException("O pagamento desta contratação está " + p.getStatus() + ".");
            }
            if (p.getValorBruto().compareTo(c.getValorAcordado()) != 0) ajustarValor(p, c);
            return p;
        }
        Empresa empresa = c.getEmpresa();
        MeioPagamentoEmpresa meio = escolherMeio(empresa.getId(), c.getVaga().getFormaPagamento());
        String chave = "contratacao-" + c.getId();
        var resultado = gateway.cobrar(meio.getTokenGateway(), c.getValorAcordado(), chave,
                "Turno " + c.getVaga().getFuncao().getNome() + " - contratação " + c.getId());
        if (!resultado.aprovado()) {
            auditoria.registrarIsolado(c.getFreelancer().getId(), "contratacao", c.getId(), "COBRANCA_RECUSADA",
                    Map.of("mensagem", resultado.mensagem()));
            throw new RegraNegocioException("O pagamento da empresa foi recusado (" + resultado.mensagem()
                    + "). A contratação continua aguardando aceite.");
        }
        Pagamento p = new Pagamento();
        p.setContratacao(c);
        p.setMeioPagamento(meio);
        definirValores(p, c.getValorAcordado());
        p.setStatus(StatusPagamento.PAGO_CUSTODIA);
        p.setIdTransacaoGateway(resultado.idTransacao());
        p.setChaveIdempotencia(chave);
        pagamentos.save(p);

        Carteira carteira = carteiraComLock(c.getFreelancer().getId());
        carteira.setSaldoPendente(carteira.getSaldoPendente().add(p.getValorLiquido()));
        auditoria.registrar("pagamento", p.getId(), "MUDANCA_STATUS", null,
                Map.of("status", "PAGO_CUSTODIA", "valorBruto", p.getValorBruto()));
        notificacoes.notificar(empresa.getId(), "PAGAMENTO",
                "Cobrança de R$ " + p.getValorBruto() + " em custódia para a contratação " + c.getId() + ".", p.getId());
        return p;
    }

    private void ajustarValor(Pagamento p, Contratacao c) {
        BigDecimal liquidoAntigo = p.getValorLiquido();
        definirValores(p, c.getValorAcordado());
        Carteira carteira = carteiraComLock(c.getFreelancer().getId());
        carteira.setSaldoPendente(carteira.getSaldoPendente().subtract(liquidoAntigo).add(p.getValorLiquido()));
        auditoria.registrar("pagamento", p.getId(), "AJUSTE_VALOR", Map.of("valorLiquido", liquidoAntigo),
                Map.of("valorLiquido", p.getValorLiquido()));
    }

    private void definirValores(Pagamento p, BigDecimal bruto) {
        BigDecimal taxa = bruto.multiply(props.regras().taxaPlataformaPercentual())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        p.setValorBruto(bruto);
        p.setTaxaPlataforma(taxa);
        p.setValorLiquido(bruto.subtract(taxa));
    }

    /** Prefere um meio do mesmo tipo da forma de pagamento da vaga (UC11). */
    private MeioPagamentoEmpresa escolherMeio(Long empresaId, FormaPagamento forma) {
        List<MeioPagamentoEmpresa> ativos = meiosPagamento.findByEmpresaIdAndAtivoTrueOrderByIdDesc(empresaId);
        if (ativos.isEmpty()) throw new RegraNegocioException("A empresa não tem meio de pagamento ativo (KAN-70 RN09).");
        return ativos.stream().filter(m -> m.getTipo().name().equals(forma.name())).findFirst().orElse(ativos.get(0));
    }

    // ------------------------------------------------------------------ check-out, liberação e estorno

    /** No check-out começa a janela de 6 h para a empresa confirmar ou abrir disputa. */
    @Transactional
    public void aguardarConfirmacao(Contratacao c) {
        pagamentos.findByContratacaoId(c.getId()).ifPresent(p -> {
            if (p.getStatus() != StatusPagamento.PAGO_CUSTODIA) return;
            p.setStatus(StatusPagamento.AGUARDANDO_CONFIRMACAO);
            LocalDateTime quando = c.getCheckoutEm().plusHours(props.regras().janelaConfirmacaoPagamentoHoras());
            fila.enfileirar(TipoTarefa.LIBERAR_PAGAMENTO, Map.of("pagamentoId", p.getId()), quando);
            auditoria.registrar("pagamento", p.getId(), "MUDANCA_STATUS", Map.of("status", "PAGO_CUSTODIA"),
                    Map.of("status", "AGUARDANDO_CONFIRMACAO", "liberacaoAutomaticaEm", quando));
        });
    }

    /** Libera o valor líquido para o saldo disponível do freelancer. */
    @Transactional
    public void liberar(Pagamento p) {
        StatusPagamento anterior = p.getStatus();
        if (anterior == StatusPagamento.LIBERADO || anterior == StatusPagamento.ESTORNADO) return; // idempotente
        Freelancer freelancer = p.getContratacao().getFreelancer();
        Carteira carteira = carteiraComLock(freelancer.getId());
        if (anterior == StatusPagamento.EM_DISPUTA) {
            carteira.setSaldoBloqueado(carteira.getSaldoBloqueado().subtract(p.getValorLiquido()));
        } else {
            carteira.setSaldoPendente(carteira.getSaldoPendente().subtract(p.getValorLiquido()));
        }
        carteira.setSaldoDisponivel(carteira.getSaldoDisponivel().add(p.getValorLiquido()));
        p.setStatus(StatusPagamento.LIBERADO);
        p.setLiberadoEm(LocalDateTime.now());

        MovimentacaoCarteira credito = new MovimentacaoCarteira();
        credito.setCarteira(carteira);
        credito.setPagamento(p);
        credito.setTipo(TipoMovimentacao.CREDITO);
        credito.setValor(p.getValorLiquido());
        credito.setStatus(StatusMovimentacao.CONCLUIDA);
        movimentacoes.save(credito);

        auditoria.registrar("pagamento", p.getId(), "MUDANCA_STATUS", Map.of("status", anterior), Map.of("status", "LIBERADO"));
        notificacoes.notificar(freelancer.getId(), "PAGAMENTO",
                "R$ " + p.getValorLiquido() + " liberados na sua carteira.", p.getId());
    }

    @Transactional
    public void liberarDaContratacao(Contratacao c) {
        pagamentos.findByContratacaoId(c.getId()).ifPresent(p -> {
            if (p.getStatus() == StatusPagamento.EM_DISPUTA) {
                throw new ConflitoException("O pagamento está em disputa e aguarda a mediação.");
            }
            liberar(p);
        });
    }

    /** Devolve o valor à empresa e desfaz o saldo pendente/bloqueado do freelancer. */
    @Transactional
    public void estornar(Pagamento p, String motivo) {
        StatusPagamento anterior = p.getStatus();
        if (anterior == StatusPagamento.ESTORNADO) return;
        if (anterior == StatusPagamento.LIBERADO) {
            throw new RegraNegocioException("Pagamento já liberado ao freelancer não pode ser estornado automaticamente.");
        }
        Freelancer freelancer = p.getContratacao().getFreelancer();
        Carteira carteira = carteiraComLock(freelancer.getId());
        if (anterior == StatusPagamento.EM_DISPUTA) {
            carteira.setSaldoBloqueado(carteira.getSaldoBloqueado().subtract(p.getValorLiquido()));
            MovimentacaoCarteira estorno = new MovimentacaoCarteira();
            estorno.setCarteira(carteira);
            estorno.setPagamento(p);
            estorno.setTipo(TipoMovimentacao.ESTORNO);
            estorno.setValor(p.getValorLiquido());
            movimentacoes.save(estorno);
        } else {
            carteira.setSaldoPendente(carteira.getSaldoPendente().subtract(p.getValorLiquido()));
        }
        gateway.estornar(p.getIdTransacaoGateway(), p.getValorBruto());
        p.setStatus(StatusPagamento.ESTORNADO);
        auditoria.registrar("pagamento", p.getId(), "MUDANCA_STATUS", Map.of("status", anterior),
                Map.of("status", "ESTORNADO", "motivo", motivo));
        notificacoes.notificar(p.getContratacao().getEmpresa().getId(), "PAGAMENTO",
                "R$ " + p.getValorBruto() + " estornados: " + motivo, p.getId());
    }

    @Transactional
    public void estornarSeHouver(Contratacao c, String motivo) {
        pagamentos.findByContratacaoId(c.getId()).ifPresent(p -> estornar(p, motivo));
    }

    // ------------------------------------------------------------------ disputa

    /** A empresa contesta horas ou comparecimento em até 6 h depois do check-out. */
    @Transactional
    public Resposta abrirDisputa(Long pagamentoId, DisputaRequest req) {
        Pagamento p = pagamentos.buscarComLock(pagamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pagamento", pagamentoId));
        Contratacao c = p.getContratacao();
        if (!c.getEmpresa().getId().equals(UsuarioAtual.id())) throw new AcessoNegadoException("Este pagamento é de outra empresa.");
        if (p.getStatus() == StatusPagamento.EM_DISPUTA) throw new ConflitoException("Já existe uma disputa aberta para este pagamento.");
        if (p.getStatus() != StatusPagamento.AGUARDANDO_CONFIRMACAO || c.getCheckoutEm() == null
                || c.getCheckoutEm().plusHours(props.regras().janelaConfirmacaoPagamentoHoras()).isBefore(LocalDateTime.now())) {
            throw new RegraNegocioException("A disputa só pode ser aberta nas "
                    + props.regras().janelaConfirmacaoPagamentoHoras() + " h seguintes ao check-out.");
        }
        Carteira carteira = carteiraComLock(c.getFreelancer().getId());
        carteira.setSaldoPendente(carteira.getSaldoPendente().subtract(p.getValorLiquido()));
        carteira.setSaldoBloqueado(carteira.getSaldoBloqueado().add(p.getValorLiquido()));
        MovimentacaoCarteira bloqueio = new MovimentacaoCarteira();
        bloqueio.setCarteira(carteira);
        bloqueio.setPagamento(p);
        bloqueio.setTipo(TipoMovimentacao.BLOQUEIO);
        bloqueio.setValor(p.getValorLiquido());
        movimentacoes.save(bloqueio);

        p.setStatus(StatusPagamento.EM_DISPUTA);
        p.setMotivoDisputa(req.motivo());
        auditoria.registrar("pagamento", p.getId(), "MUDANCA_STATUS", Map.of("status", "AGUARDANDO_CONFIRMACAO"),
                Map.of("status", "EM_DISPUTA", "motivo", req.motivo()));
        notificacoes.notificar(c.getFreelancer().getId(), "PAGAMENTO",
                "A empresa abriu uma disputa sobre o turno " + c.getId() + ". O valor fica bloqueado até a mediação.", p.getId());
        return resposta(p);
    }

    /** Mediação (Administrador): libera para o freelancer ou estorna para a empresa. */
    @Transactional
    public Resposta decidirDisputa(Long pagamentoId, DecisaoDisputaRequest req) {
        Pagamento p = pagamentos.buscarComLock(pagamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pagamento", pagamentoId));
        if (p.getStatus() != StatusPagamento.EM_DISPUTA) throw new ConflitoException("Este pagamento não está em disputa.");
        if (req.favoravelAoFreelancer()) {
            liberar(p);
            Contratacao c = p.getContratacao();
            if (c.getStatus() == StatusContratacao.EM_ANDAMENTO) {
                c.setStatus(StatusContratacao.CONCLUIDA);
                c.setEncerradaEm(LocalDateTime.now());
            }
        } else {
            estornar(p, "Disputa decidida a favor da empresa" + (req.observacao() == null ? "" : ": " + req.observacao()));
        }
        return resposta(p);
    }

    // ------------------------------------------------------------------ consultas

    @Transactional(readOnly = true)
    public Resposta consultarDaContratacao(Long contratacaoId) {
        Contratacao c = contratacoes.findById(contratacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Contratação", contratacaoId));
        Long logado = UsuarioAtual.id();
        boolean participa = c.getEmpresa().getId().equals(logado) || c.getFreelancer().getId().equals(logado)
                || UsuarioAtual.perfil() == TipoUsuario.ADMINISTRADOR;
        if (!participa) throw new AcessoNegadoException("Esta contratação é de outra conta.");
        Pagamento p = pagamentos.findByContratacaoId(contratacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Esta contratação ainda não tem pagamento (a cobrança acontece no aceite do freelancer)."));
        return resposta(p);
    }

    @Transactional(readOnly = true)
    public PaginaResposta<Resposta> listarDaEmpresa(Long empresaId, StatusPagamento status, LocalDateTime de,
                                                    LocalDateTime ate, int pagina, int tamanho) {
        Contas.exigirMesmoUsuario(empresaId, UsuarioAtual.id());
        LocalDateTime inicio = de == null ? LocalDateTime.of(2000, 1, 1, 0, 0) : de;
        LocalDateTime fim = ate == null ? LocalDateTime.now().plusYears(1) : ate;
        var page = pagamentos.listarDaEmpresa(empresaId, status, inicio, fim,
                Paginas.de(pagina, tamanho, Sort.by(Sort.Direction.DESC, "pagoEm")));
        return PaginaResposta.de(page, this::resposta);
    }

    @Transactional(readOnly = true)
    public PaginaResposta<Resposta> listarEmDisputa(int pagina, int tamanho) {
        return PaginaResposta.de(pagamentos.findByStatus(StatusPagamento.EM_DISPUTA,
                Paginas.de(pagina, tamanho, Sort.by("pagoEm"))), this::resposta);
    }

    // ------------------------------------------------------------------ webhook do gateway

    /** Confere a assinatura HMAC e põe o evento na fila; o gateway recebe 200 na hora. */
    @Transactional
    public void receberWebhook(String corpo, String assinatura, WebhookRequest evento) {
        String esperada = Hashes.hmacSha256(corpo, props.seguranca().webhookSegredo());
        if (!Hashes.iguais(esperada, assinatura)) {
            throw new NaoAutenticadoException("Assinatura do webhook inválida.");
        }
        fila.enfileirar(TipoTarefa.PROCESSAR_WEBHOOK, Map.of(
                "idTransacaoGateway", evento.idTransacaoGateway(),
                "evento", evento.evento(),
                "status", evento.status() == null ? "" : evento.status()));
    }

    /** Executado pelo worker. Evento repetido não muda nada (idempotente). */
    @Transactional
    public void processarWebhook(JsonNode payload) {
        String idTransacao = payload.get("idTransacaoGateway").asString();
        String evento = payload.get("evento").asString();
        pagamentos.findByIdTransacaoGateway(idTransacao).ifPresentOrElse(p -> {
            switch (evento) {
                case "PAGAMENTO_CONFIRMADO" -> log.info("Webhook: pagamento {} confirmado ({}).", p.getId(), p.getStatus());
                case "PAGAMENTO_ESTORNADO", "CHARGEBACK" -> {
                    if (p.getStatus() != StatusPagamento.ESTORNADO && p.getStatus() != StatusPagamento.LIBERADO) {
                        estornar(p, "Estorno informado pelo gateway (" + evento + ")");
                    }
                }
                default -> log.info("Webhook: evento {} ignorado para o pagamento {}.", evento, p.getId());
            }
            auditoria.registrar("pagamento", p.getId(), "WEBHOOK", null, Map.of("evento", evento));
        }, () -> log.info("Webhook: transação {} não é de pagamento de contratação (evento {}).", idTransacao, evento));
    }

    // ------------------------------------------------------------------ auxiliares

    private Carteira carteiraComLock(Long freelancerId) {
        return carteiras.buscarComLockPorFreelancer(freelancerId)
                .orElseThrow(() -> new IllegalStateException("Freelancer " + freelancerId + " sem carteira"));
    }

    public Resposta resposta(Pagamento p) {
        LocalDateTime automatica = p.getStatus() == StatusPagamento.AGUARDANDO_CONFIRMACAO && p.getContratacao().getCheckoutEm() != null
                ? p.getContratacao().getCheckoutEm().plusHours(props.regras().janelaConfirmacaoPagamentoHoras()) : null;
        return Resposta.de(p, automatica);
    }

    public JsonMapper jsonMapper() { return jsonMapper; }
}
