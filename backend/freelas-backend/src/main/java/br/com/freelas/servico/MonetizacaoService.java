package br.com.freelas.servico;

import br.com.freelas.dto.MonetizacaoDto.*;
import br.com.freelas.entidade.*;
import br.com.freelas.enums.*;
import br.com.freelas.excecao.*;
import br.com.freelas.integracao.GatewayPagamento;
import br.com.freelas.repositorio.*;
import br.com.freelas.seguranca.UsuarioAtual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Premium (empresa e freelancer), destaque de vaga e Boost de perfil.
 * Toda compra exige Idempotency-Key: repetir a mesma chave devolve a compra já feita, sem cobrar de novo.
 */
@Service
public class MonetizacaoService {

    private final PlanoRepository planos;
    private final AssinaturaRepository assinaturas;
    private final ImpulsionamentoRepository impulsionamentos;
    private final MeioPagamentoEmpresaRepository meiosPagamento;
    private final ContaRecebimentoRepository contas;
    private final OcorrenciaRepository ocorrencias;
    private final UsuarioRepository usuarios;
    private final VagaService vagaService;
    private final FreelancerService freelancerService;
    private final GatewayPagamento gateway;
    private final AuditoriaService auditoria;

    public MonetizacaoService(PlanoRepository planos, AssinaturaRepository assinaturas,
                              ImpulsionamentoRepository impulsionamentos, MeioPagamentoEmpresaRepository meiosPagamento,
                              ContaRecebimentoRepository contas, OcorrenciaRepository ocorrencias,
                              UsuarioRepository usuarios, VagaService vagaService, FreelancerService freelancerService,
                              GatewayPagamento gateway, AuditoriaService auditoria) {
        this.planos = planos;
        this.assinaturas = assinaturas;
        this.impulsionamentos = impulsionamentos;
        this.meiosPagamento = meiosPagamento;
        this.contas = contas;
        this.ocorrencias = ocorrencias;
        this.usuarios = usuarios;
        this.vagaService = vagaService;
        this.freelancerService = freelancerService;
        this.gateway = gateway;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<PlanoResposta> listarPlanos(PublicoPlano publico) {
        List<Plano> lista = publico == null ? planos.findAll() : planos.findByPublicoOrderByPrecoAsc(publico);
        return lista.stream().map(PlanoResposta::de).toList();
    }

    // ------------------------------------------------------------------ Premium

    /** UC14: só fica ATIVA depois que o gateway aprova; se falhar, nada é ativado. */
    @Transactional
    public Resultado<AssinaturaResposta> assinarEmpresa(Long empresaId, AssinaturaEmpresaRequest req, String chave) {
        Contas.exigirMesmoUsuario(empresaId, UsuarioAtual.id());
        Usuario empresa = usuarios.findById(empresaId).orElseThrow();
        MeioPagamentoEmpresa meio = meiosPagamento.findByIdAndEmpresaId(req.meioPagamentoId(), empresaId)
                .filter(MeioPagamentoEmpresa::getAtivo)
                .orElseThrow(() -> new RegraNegocioException("Meio de pagamento " + req.meioPagamentoId() + " não está ativo."));
        return assinar(empresa, req.planoId(), PublicoPlano.EMPRESA, chave,
                (plano, k) -> gateway.cobrar(meio.getTokenGateway(), plano.getPreco(), k, "Assinatura " + plano.getNome()));
    }

    @Transactional
    public Resultado<AssinaturaResposta> assinarFreelancer(AssinaturaFreelancerRequest req, String chave) {
        Usuario freelancer = freelancerService.logado();
        return assinar(freelancer, req.planoId(), PublicoPlano.FREELANCER, chave,
                (plano, k) -> gateway.cobrarPixAvulso(plano.getPreco(), k, "Assinatura " + plano.getNome()));
    }

    private Resultado<AssinaturaResposta> assinar(Usuario usuario, Integer planoId, PublicoPlano publico, String chave,
                                                  java.util.function.BiFunction<Plano, String, GatewayPagamento.Resultado> cobranca) {
        Contas.exigirAtiva(usuario);
        Plano plano = planos.findById(planoId).orElseThrow(() -> new RecursoNaoEncontradoException("Plano", planoId));
        if (plano.getPublico() != publico) throw new RegraNegocioException("Este plano é para " + plano.getPublico() + ".");
        // Checa antes de cobrar, para nunca cobrar uma assinatura que não seria ativada
        if (!assinaturas.vigentes(usuario.getId(), LocalDateTime.now()).isEmpty()) {
            throw new ConflitoException("Você já tem uma assinatura Premium vigente.");
        }
        String chaveCompleta = "assinatura-" + usuario.getId() + "-" + chave;
        var resultado = cobranca.apply(plano, chaveCompleta);
        if (!resultado.aprovado()) throw new RegraNegocioException("Pagamento não aprovado: " + resultado.mensagem() + " A assinatura não foi ativada.");
        var existente = assinaturas.findByIdTransacaoGateway(resultado.idTransacao());
        if (existente.isPresent()) return new Resultado<>(AssinaturaResposta.de(existente.get()), false);
        Assinatura a = new Assinatura();
        a.setUsuario(usuario);
        a.setPlano(plano);
        a.setInicio(LocalDateTime.now());
        a.setFim(a.getInicio().plusDays(plano.getDuracaoDias()));
        a.setStatus(StatusAssinatura.ATIVA);
        a.setIdTransacaoGateway(resultado.idTransacao());
        assinaturas.save(a);
        auditoria.registrar("assinatura", a.getId(), "CRIACAO", null, Map.of("plano", plano.getNome(), "valor", plano.getPreco()));
        return new Resultado<>(AssinaturaResposta.de(a), true);
    }

    @Transactional(readOnly = true)
    public List<AssinaturaResposta> listarAssinaturas(Long usuarioId) {
        Contas.exigirMesmoUsuario(usuarioId, UsuarioAtual.id());
        return assinaturas.findByUsuarioIdOrderByInicioDesc(usuarioId).stream().map(AssinaturaResposta::de).toList();
    }

    @Transactional(readOnly = true)
    public AssinaturaResposta assinaturaAtual() {
        return assinaturas.vigentes(UsuarioAtual.id(), LocalDateTime.now()).stream().findFirst()
                .map(AssinaturaResposta::de)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Você não tem assinatura Premium vigente."));
    }

    @Transactional
    public AssinaturaResposta alterarRenovacao(Long id, RenovacaoRequest req) {
        Assinatura a = minhaAssinatura(id);
        if (a.getStatus() != StatusAssinatura.ATIVA) throw new ConflitoException("A assinatura está " + a.getStatus() + ".");
        a.setRenovacaoAutomatica(req.renovacaoAutomatica());
        return AssinaturaResposta.de(a);
    }

    /** Cancela a renovação; o acesso continua até o fim do período pago. */
    @Transactional
    public AssinaturaResposta cancelarAssinatura(Long id) {
        Assinatura a = minhaAssinatura(id);
        if (a.getStatus() != StatusAssinatura.ATIVA) throw new ConflitoException("A assinatura está " + a.getStatus() + ".");
        a.setStatus(StatusAssinatura.CANCELADA);
        a.setRenovacaoAutomatica(false);
        auditoria.registrar("assinatura", id, "MUDANCA_STATUS", Map.of("status", "ATIVA"), Map.of("status", "CANCELADA"));
        return AssinaturaResposta.de(a);
    }

    @Transactional
    public AssinaturaResposta cancelarAssinaturaAtual() {
        return cancelarAssinatura(assinaturaAtual().id());
    }

    private Assinatura minhaAssinatura(Long id) {
        Assinatura a = assinaturas.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Assinatura", id));
        if (!a.getUsuario().getId().equals(UsuarioAtual.id())) throw new AcessoNegadoException("Esta assinatura é de outra conta.");
        return a;
    }

    // ------------------------------------------------------------------ destaque de vaga e Boost

    public List<OpcaoResposta> opcoes(TipoImpulsionamento tipo) {
        return Arrays.stream(OpcaoDestaque.values()).filter(o -> o.getTipo() == tipo).map(OpcaoResposta::de).toList();
    }

    /** UC15: só vaga PUBLICADA. Se já existe destaque ativo, o novo começa quando o atual termina. */
    @Transactional
    public Resultado<ImpulsionamentoResposta> destacarVaga(Long vagaId, DestaqueRequest req, String chave) {
        Vaga v = vagaService.daEmpresaLogada(vagaId);
        Contas.exigirAtiva(v.getEmpresa());
        if (req.opcao().getTipo() != TipoImpulsionamento.DESTAQUE_VAGA) {
            throw new RegraNegocioException("Opção inválida para vaga. Use VAGA_24H ou VAGA_7D.");
        }
        if (v.getStatus() != StatusVaga.PUBLICADA) throw new RegraNegocioException("Só vaga PUBLICADA pode receber destaque.");
        MeioPagamentoEmpresa meio = meiosPagamento.findByEmpresaIdAndAtivoTrueOrderByIdDesc(v.getEmpresa().getId())
                .stream().findFirst().orElseThrow(() -> new RegraNegocioException("Cadastre um meio de pagamento ativo."));
        var resultado = gateway.cobrar(meio.getTokenGateway(), req.opcao().getPreco(), "destaque-" + vagaId + "-" + chave,
                "Destaque da vaga " + vagaId);
        if (!resultado.aprovado()) throw new RegraNegocioException("Pagamento não aprovado: " + resultado.mensagem() + " O destaque não foi ativado.");
        var existente = impulsionamentos.findByIdTransacaoGateway(resultado.idTransacao());
        if (existente.isPresent()) return new Resultado<>(ImpulsionamentoResposta.de(existente.get()), false);

        LocalDateTime inicio = impulsionamentos.findByVagaIdOrderByInicioDesc(vagaId).stream()
                .map(Impulsionamento::getFim).filter(f -> f.isAfter(LocalDateTime.now())).findFirst()
                .orElse(LocalDateTime.now());
        Impulsionamento i = new Impulsionamento();
        i.setTipo(TipoImpulsionamento.DESTAQUE_VAGA);
        i.setVaga(v);
        i.setInicio(inicio);
        i.setFim(inicio.plus(req.opcao().getDuracao()));
        i.setValor(req.opcao().getPreco());
        i.setIdTransacaoGateway(resultado.idTransacao());
        impulsionamentos.save(i);
        auditoria.registrar("impulsionamento", i.getId(), "CRIACAO", null, Map.of("vagaId", vagaId, "opcao", req.opcao()));
        return new Resultado<>(ImpulsionamentoResposta.de(i), true);
    }

    @Transactional(readOnly = true)
    public List<ImpulsionamentoResposta> destaquesDaVaga(Long vagaId) {
        vagaService.daEmpresaLogada(vagaId);
        return impulsionamentos.findByVagaIdOrderByInicioDesc(vagaId).stream().map(ImpulsionamentoResposta::de).toList();
    }

    /** Boost: perfil 100% completo, chave Pix homologada e sem no-show nos últimos 30 dias. */
    @Transactional
    public Resultado<ImpulsionamentoResposta> comprarBoost(BoostRequest req, String chave) {
        Freelancer f = freelancerService.logado();
        Contas.exigirAtiva(f);
        OpcaoDestaque opcao = req.opcao() == null ? OpcaoDestaque.BOOST_24H : req.opcao();
        if (opcao.getTipo() != TipoImpulsionamento.BOOST_PERFIL) throw new RegraNegocioException("Opção inválida para Boost. Use BOOST_24H.");
        var completude = freelancerService.completude(f);
        if (!completude.completo()) throw new RegraNegocioException("Complete o perfil para usar o Boost.", completude.pendencias());
        boolean pixHomologado = contas.findByFreelancerIdAndStatusNotOrderByCriadaEmDesc(f.getId(), StatusContaRecebimento.INATIVA)
                .stream().anyMatch(c -> c.getTipo() == TipoContaRecebimento.PIX && !c.getLiberadaSaqueEm().isAfter(LocalDateTime.now()));
        if (!pixHomologado) throw new RegraNegocioException("Cadastre uma chave Pix e aguarde a liberação de 24 h para usar o Boost.");
        if (ocorrencias.existsByUsuarioIdAndTipoAndRegistradaEmAfter(f.getId(), TipoOcorrencia.NO_SHOW, LocalDateTime.now().minusDays(30))) {
            throw new RegraNegocioException("O Boost fica indisponível por 30 dias depois de uma falta (no-show).");
        }
        if (impulsionamentos.existsByFreelancerIdAndFimAfter(f.getId(), LocalDateTime.now())) {
            throw new ConflitoException("Você já tem um Boost ativo.");
        }
        var resultado = gateway.cobrarPixAvulso(opcao.getPreco(), "boost-" + f.getId() + "-" + chave, "Boost de perfil");
        var existente = impulsionamentos.findByIdTransacaoGateway(resultado.idTransacao());
        if (existente.isPresent()) return new Resultado<>(ImpulsionamentoResposta.de(existente.get()), false);
        Impulsionamento i = new Impulsionamento();
        i.setTipo(TipoImpulsionamento.BOOST_PERFIL);
        i.setFreelancer(f);
        i.setInicio(LocalDateTime.now());
        i.setFim(i.getInicio().plus(opcao.getDuracao()));
        i.setValor(opcao.getPreco());
        i.setIdTransacaoGateway(resultado.idTransacao());
        impulsionamentos.save(i);
        auditoria.registrar("impulsionamento", i.getId(), "CRIACAO", null, Map.of("opcao", opcao));
        return new Resultado<>(ImpulsionamentoResposta.de(i), true);
    }

    @Transactional(readOnly = true)
    public List<ImpulsionamentoResposta> meusBoosts() {
        return impulsionamentos.findByFreelancerIdOrderByInicioDesc(UsuarioAtual.id()).stream().map(ImpulsionamentoResposta::de).toList();
    }

    /** Resposta + se foi criada agora (201) ou se é repetição da mesma Idempotency-Key (200). */
    public record Resultado<T>(T corpo, boolean criado) {}
}
