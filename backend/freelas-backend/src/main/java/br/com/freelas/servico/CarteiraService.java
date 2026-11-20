package br.com.freelas.servico;

import br.com.freelas.config.PropriedadesFreelas;
import br.com.freelas.dto.CarteiraDto.*;
import br.com.freelas.dto.PaginaResposta;
import br.com.freelas.dto.Validacoes;
import br.com.freelas.entidade.*;
import br.com.freelas.enums.*;
import br.com.freelas.excecao.*;
import br.com.freelas.fila.FilaService;
import br.com.freelas.integracao.GatewayPagamento;
import br.com.freelas.repositorio.*;
import br.com.freelas.seguranca.CriptografiaService;
import br.com.freelas.seguranca.UsuarioAtual;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Carteira do freelancer: saldos, extrato, chave Pix/conta (quarentena de 24 h) e saque Pix
 * (RN-CART-14, RN-CART-16, RF9, RF12). O saldo é sempre alterado com a carteira travada (FOR UPDATE).
 */
@Service
public class CarteiraService {

    private final CarteiraRepository carteiras;
    private final MovimentacaoCarteiraRepository movimentacoes;
    private final ContaRecebimentoRepository contas;
    private final ContratacaoRepository contratacoes;
    private final FreelancerService freelancerService;
    private final CriptografiaService cripto;
    private final GatewayPagamento gateway;
    private final NotificacaoService notificacoes;
    private final AuditoriaService auditoria;
    private final FilaService fila;
    private final PropriedadesFreelas props;

    public CarteiraService(CarteiraRepository carteiras, MovimentacaoCarteiraRepository movimentacoes,
                           ContaRecebimentoRepository contas, ContratacaoRepository contratacoes,
                           FreelancerService freelancerService, CriptografiaService cripto, GatewayPagamento gateway,
                           NotificacaoService notificacoes, AuditoriaService auditoria, FilaService fila,
                           PropriedadesFreelas props) {
        this.carteiras = carteiras;
        this.movimentacoes = movimentacoes;
        this.contas = contas;
        this.contratacoes = contratacoes;
        this.freelancerService = freelancerService;
        this.cripto = cripto;
        this.gateway = gateway;
        this.notificacoes = notificacoes;
        this.auditoria = auditoria;
        this.fila = fila;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public SaldosResposta saldos() {
        Carteira c = minhaCarteira();
        return new SaldosResposta(c.getSaldoDisponivel(), c.getSaldoPendente(), c.getSaldoBloqueado(),
                c.getSaldoDisponivel().add(c.getSaldoPendente()).add(c.getSaldoBloqueado()));
    }

    @Transactional(readOnly = true)
    public PaginaResposta<MovimentacaoResposta> extrato(TipoMovimentacao tipo, LocalDate de, LocalDate ate, int pagina, int tamanho) {
        Carteira c = minhaCarteira();
        LocalDateTime inicio = de == null ? LocalDateTime.of(2000, 1, 1, 0, 0) : de.atStartOfDay();
        LocalDateTime fim = ate == null ? LocalDateTime.now().plusDays(1) : ate.plusDays(1).atStartOfDay();
        var page = movimentacoes.extrato(c.getId(), tipo, inicio, fim, Paginas.de(pagina, tamanho, Sort.by(Sort.Direction.DESC, "criadaEm")));
        return PaginaResposta.de(page, this::movimentacao);
    }

    // ------------------------------------------------------------------ dados de recebimento

    @Transactional(readOnly = true)
    public List<ContaRecebimentoResposta> listarContas() {
        return contas.findByFreelancerIdAndStatusNotOrderByCriadaEmDesc(UsuarioAtual.id(), StatusContaRecebimento.INATIVA)
                .stream().map(this::conta).toList();
    }

    /** Cadastro de chave Pix ou conta: começa em quarentena de 24 h para saques (RN-CART-16). */
    @Transactional
    public ContaRecebimentoResposta cadastrarConta(ContaRecebimentoRequest req) {
        Freelancer f = freelancerService.logado();
        Contas.exigirAtiva(f);
        ContaRecebimento c = new ContaRecebimento();
        c.setFreelancer(f);
        preencher(c, req, f);
        contas.save(c);
        auditoria.registrar("conta_recebimento", c.getId(), "CADASTRO", null, Map.of("tipo", req.tipo()));
        notificacoes.notificar(f.getId(), "SEGURANCA", "Novo dado de recebimento cadastrado. Saques para ele liberados em "
                + props.regras().quarentenaContaRecebimentoHoras() + " h.", c.getId());
        return conta(c);
    }

    /** Alterar a chave ou conta reinicia a quarentena. */
    @Transactional
    public ContaRecebimentoResposta alterarConta(Long id, ContaRecebimentoRequest req) {
        Freelancer f = freelancerService.logado();
        Contas.exigirAtiva(f);
        ContaRecebimento c = contas.findByIdAndFreelancerId(id, f.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Dado de recebimento", id));
        preencher(c, req, f);
        auditoria.registrar("conta_recebimento", id, "ALTERACAO", null, Map.of("tipo", req.tipo()));
        notificacoes.notificar(f.getId(), "SEGURANCA", "Seu dado de recebimento foi alterado. Se não foi você, fale com o suporte.", id);
        return conta(c);
    }

    private void preencher(ContaRecebimento c, ContaRecebimentoRequest req, Freelancer f) {
        // Saque só para conta do mesmo CPF (RN-CART-14): a chave CPF precisa ser a do cadastro
        if (req.tipo() == TipoContaRecebimento.PIX && req.tipoChavePix() == TipoChavePix.CPF
                && !Validacoes.soDigitos(req.chavePix()).equals(f.getCpf())) {
            throw new RegraNegocioException("A chave Pix CPF precisa ser o CPF do seu cadastro (mesma titularidade).");
        }
        c.setTipo(req.tipo());
        c.setTipoChavePix(req.tipo() == TipoContaRecebimento.PIX ? req.tipoChavePix() : null);
        c.setChavePixCript(req.tipo() == TipoContaRecebimento.PIX ? cripto.criptografar(req.chavePix().trim()) : null);
        c.setBanco(req.tipo() == TipoContaRecebimento.CONTA_BANCARIA ? req.banco() : null);
        c.setAgenciaCript(req.tipo() == TipoContaRecebimento.CONTA_BANCARIA ? cripto.criptografar(req.agencia()) : null);
        c.setContaCript(req.tipo() == TipoContaRecebimento.CONTA_BANCARIA ? cripto.criptografar(req.conta()) : null);
        c.setStatus(StatusContaRecebimento.EM_QUARENTENA);
        c.setLiberadaSaqueEm(LocalDateTime.now().plusHours(props.regras().quarentenaContaRecebimentoHoras()));
    }

    // ------------------------------------------------------------------ saques

    /** Saque Pix: usa só o saldo disponível e exige Idempotency-Key. O envio ao gateway vai pela fila. */
    @Transactional
    public SaqueResultado sacar(SaqueRequest req, String chaveIdempotencia) {
        Long freelancerId = UsuarioAtual.id();
        var repetido = movimentacoes.findByChaveIdempotencia("saque-" + freelancerId + "-" + chaveIdempotencia);
        if (repetido.isPresent()) return new SaqueResultado(saque(repetido.get()), false); // mesmo pedido repetido: não saca de novo

        Freelancer f = freelancerService.logado();
        Contas.exigirAtiva(f);
        ContaRecebimento conta = contas.findByIdAndFreelancerId(req.contaRecebimentoId(), freelancerId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Dado de recebimento", req.contaRecebimentoId()));
        if (conta.getStatus() == StatusContaRecebimento.INATIVA) throw new RegraNegocioException("Este dado de recebimento está inativo.");
        if (conta.getLiberadaSaqueEm().isAfter(LocalDateTime.now())) {
            throw new RegraNegocioException("Por segurança, saques para este dado ficam liberados em " + conta.getLiberadaSaqueEm() + " (UTC).");
        }
        if (conta.getTipo() != TipoContaRecebimento.PIX) throw new RegraNegocioException("O saque é feito por Pix: escolha uma chave Pix.");
        conta.setStatus(StatusContaRecebimento.ATIVA);

        Carteira carteira = carteiras.buscarComLockPorFreelancer(freelancerId).orElseThrow();
        if (carteira.getSaldoDisponivel().compareTo(req.valor()) < 0) {
            throw new RegraNegocioException("Saldo disponível insuficiente (R$ " + carteira.getSaldoDisponivel() + ").");
        }
        carteira.setSaldoDisponivel(carteira.getSaldoDisponivel().subtract(req.valor()));
        MovimentacaoCarteira m = new MovimentacaoCarteira();
        m.setCarteira(carteira);
        m.setContaRecebimento(conta);
        m.setTipo(TipoMovimentacao.SAQUE);
        m.setValor(req.valor());
        m.setStatus(StatusMovimentacao.PENDENTE);
        m.setChaveIdempotencia("saque-" + freelancerId + "-" + chaveIdempotencia);
        movimentacoes.save(m);
        fila.enfileirar(TipoTarefa.PROCESSAR_SAQUE, Map.of("movimentacaoId", m.getId()));
        auditoria.registrar("movimentacao_carteira", m.getId(), "SAQUE_SOLICITADO", null, Map.of("valor", req.valor()));
        return new SaqueResultado(saque(m), true);
    }

    /** Worker: envia o Pix. Se falhar, o valor volta para o saldo disponível sem duplicar. */
    @Transactional
    public void processarSaque(Long movimentacaoId) {
        MovimentacaoCarteira m = movimentacoes.findById(movimentacaoId).orElse(null);
        if (m == null || m.getStatus() != StatusMovimentacao.PENDENTE) return;
        ContaRecebimento conta = m.getContaRecebimento();
        var resultado = gateway.enviarPix(cripto.descriptografar(conta.getChavePixCript()), m.getValor(), m.getChaveIdempotencia());
        Long freelancerId = m.getCarteira().getFreelancer().getId();
        if (resultado.aprovado()) {
            m.setStatus(StatusMovimentacao.CONCLUIDA);
            notificacoes.notificar(freelancerId, "SAQUE", "Saque de R$ " + m.getValor() + " enviado por Pix.", m.getId());
        } else {
            Carteira carteira = carteiras.buscarComLockPorFreelancer(freelancerId).orElseThrow();
            carteira.setSaldoDisponivel(carteira.getSaldoDisponivel().add(m.getValor()));
            m.setStatus(StatusMovimentacao.FALHOU);
            notificacoes.notificar(freelancerId, "SAQUE", "O saque de R$ " + m.getValor() + " falhou e o valor voltou para a carteira.", m.getId());
        }
    }

    @Transactional(readOnly = true)
    public PaginaResposta<SaqueResposta> listarSaques(int pagina, int tamanho) {
        Carteira c = minhaCarteira();
        var page = movimentacoes.extrato(c.getId(), TipoMovimentacao.SAQUE, LocalDateTime.of(2000, 1, 1, 0, 0),
                LocalDateTime.now().plusDays(1), Paginas.de(pagina, tamanho, Sort.by(Sort.Direction.DESC, "criadaEm")));
        return PaginaResposta.de(page, this::saque);
    }

    @Transactional(readOnly = true)
    public SaqueResposta consultarSaque(Long id) {
        Carteira c = minhaCarteira();
        return saque(movimentacoes.findByIdAndCarteiraIdAndTipo(id, c.getId(), TipoMovimentacao.SAQUE)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Saque", id)));
    }

    // ------------------------------------------------------------------ painel de ganhos

    @Transactional(readOnly = true)
    public GanhosResposta ganhos(Periodo periodo) {
        Periodo p = periodo == null ? Periodo.MES : periodo;
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime desde = switch (p) {
            case HOJE -> agora.toLocalDate().atStartOfDay();
            case SETE_DIAS -> agora.minusDays(7);
            case MES -> agora.toLocalDate().withDayOfMonth(1).atStartOfDay();
            case TOTAL -> LocalDateTime.of(2000, 1, 1, 0, 0);
        };
        Long id = UsuarioAtual.id();
        BigDecimal total = contratacoes.somarGanhosLiberados(id, desde);
        var concluidas = contratacoes.concluidasDoFreelancerDesde(id, desde);
        long minutos = concluidas.stream()
                .filter(c -> c.getCheckinEm() != null && c.getCheckoutEm() != null)
                .mapToLong(c -> Duration.between(c.getCheckinEm(), c.getCheckoutEm()).toMinutes()).sum();
        BigDecimal horas = BigDecimal.valueOf(minutos).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        BigDecimal porHora = horas.signum() == 0 ? BigDecimal.ZERO : total.divide(horas, 2, RoundingMode.HALF_UP);
        return new GanhosResposta(p, total, concluidas.size(), horas, porHora);
    }

    // ------------------------------------------------------------------ auxiliares

    private Carteira minhaCarteira() {
        return carteiras.findByFreelancerId(UsuarioAtual.id())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Carteira não encontrada para este usuário."));
    }

    private MovimentacaoResposta movimentacao(MovimentacaoCarteira m) {
        Pagamento p = m.getPagamento();
        if (p == null) {
            return new MovimentacaoResposta(m.getId(), m.getTipo(), m.getValor(), m.getStatus(), m.getCriadaEm(),
                    null, null, null, null, null, null, null, null,
                    m.getContaRecebimento() == null ? null : m.getContaRecebimento().getId());
        }
        Vaga v = p.getContratacao().getVaga();
        return new MovimentacaoResposta(m.getId(), m.getTipo(), m.getValor(), m.getStatus(), m.getCriadaEm(), p.getId(),
                v.getId(), v.getEmpresa().getNomeFantasia(), v.getInicio(), v.getFim(), p.getValorBruto(),
                p.getTaxaPlataforma(), p.getValorLiquido(), null);
    }

    private ContaRecebimentoResposta conta(ContaRecebimento c) {
        return new ContaRecebimentoResposta(c.getId(), c.getTipo(), c.getTipoChavePix(),
                CriptografiaService.mascarar(cripto.descriptografar(c.getChavePixCript())), c.getBanco(),
                CriptografiaService.mascarar(cripto.descriptografar(c.getAgenciaCript())),
                CriptografiaService.mascarar(cripto.descriptografar(c.getContaCript())),
                c.getStatus(), c.getLiberadaSaqueEm(), c.getCriadaEm());
    }

    private SaqueResposta saque(MovimentacaoCarteira m) {
        return new SaqueResposta(m.getId(), m.getValor(), m.getStatus(),
                m.getContaRecebimento() == null ? null : m.getContaRecebimento().getId(), m.getCriadaEm());
    }
    public record SaqueResultado(SaqueResposta saque, boolean criado) {}
}
