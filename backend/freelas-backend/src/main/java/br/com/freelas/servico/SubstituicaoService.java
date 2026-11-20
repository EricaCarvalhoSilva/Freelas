package br.com.freelas.servico;

import br.com.freelas.config.PropriedadesFreelas;
import br.com.freelas.dto.SubstituicaoDto.*;
import br.com.freelas.entidade.*;
import br.com.freelas.enums.*;
import br.com.freelas.excecao.*;
import br.com.freelas.fila.FilaService;
import br.com.freelas.repositorio.*;
import br.com.freelas.seguranca.UsuarioAtual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Substituição automática (UC13, RF16, RF17, "Solicitação de substituição").
 * A busca dos elegíveis roda na fila; todos são avisados de uma vez e o primeiro que aceitar fica com a vaga.
 */
@Service
public class SubstituicaoService {

    private static final List<StatusSubstituicao> ABERTAS =
            List.of(StatusSubstituicao.AGUARDANDO_SUBSTITUTO, StatusSubstituicao.SEM_CANDIDATOS);

    private final SubstituicaoRepository substituicoes;
    private final ContratacaoRepository contratacoes;
    private final CandidaturaRepository candidaturas;
    private final FreelancerRepository freelancers;
    private final NotificacaoRepository notificacaoRepo;
    private final PagamentoService pagamentoService;
    private final NotificacaoService notificacoes;
    private final AuditoriaService auditoria;
    private final FilaService fila;
    private final PropriedadesFreelas props;

    public SubstituicaoService(SubstituicaoRepository substituicoes, ContratacaoRepository contratacoes,
                               CandidaturaRepository candidaturas, FreelancerRepository freelancers,
                               NotificacaoRepository notificacaoRepo, PagamentoService pagamentoService,
                               NotificacaoService notificacoes, AuditoriaService auditoria, FilaService fila,
                               PropriedadesFreelas props) {
        this.substituicoes = substituicoes;
        this.contratacoes = contratacoes;
        this.candidaturas = candidaturas;
        this.freelancers = freelancers;
        this.notificacaoRepo = notificacaoRepo;
        this.pagamentoService = pagamentoService;
        this.notificacoes = notificacoes;
        this.auditoria = auditoria;
        this.fila = fila;
        this.props = props;
    }

    /** A empresa pede o substituto: só com o turno confirmado ou em andamento e antes do check-out. */
    @Transactional
    public Resposta solicitar(Long contratacaoId, Request req) {
        Contratacao c = contratacoes.findById(contratacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Contratação", contratacaoId));
        if (!c.getEmpresa().getId().equals(UsuarioAtual.id())) throw new AcessoNegadoException("Esta contratação é de outra empresa.");
        if ((c.getStatus() != StatusContratacao.CONFIRMADA && c.getStatus() != StatusContratacao.EM_ANDAMENTO
                && c.getStatus() != StatusContratacao.NO_SHOW) || c.getCheckoutEm() != null) {
            throw new RegraNegocioException("A substituição só vale para contratação confirmada, em andamento ou com no-show, antes do check-out.");
        }
        return resposta(criar(c, req.motivo(), req.raioKm()));
    }

    /** Disparada quando o freelancer cancela um turno confirmado. */
    @Transactional
    public Substituicao criarAutomatica(Contratacao c, MotivoSubstituicao motivo) {
        return criar(c, motivo, props.regras().raioPadraoSubstituicaoKm());
    }

    private Substituicao criar(Contratacao c, MotivoSubstituicao motivo, BigDecimal raioKm) {
        if (substituicoes.existsByContratacaoOriginalIdAndStatusIn(c.getId(), ABERTAS)) {
            throw new ConflitoException("Já existe uma substituição aberta para esta contratação.");
        }
        Substituicao s = new Substituicao();
        s.setContratacaoOriginal(c);
        s.setMotivo(motivo);
        s.setRaioKm(raioKm);
        substituicoes.save(s);
        fila.enfileirar(TipoTarefa.BUSCAR_SUBSTITUTOS, Map.of("substituicaoId", s.getId()));
        auditoria.registrar("substituicao", s.getId(), "CRIACAO", null, Map.of("motivo", motivo, "raioKm", raioKm));
        return s;
    }

    /**
     * Executado pelo worker: encontra os elegíveis no raio e avisa todos (RNF20).
     * Raio até 5 km = mesmo início de CEP; até 30 km = mesma cidade; acima = mesma UF.
     * Quando a integração com o Google Maps entrar, troque esta aproximação pelo cálculo da distância.
     */
    @Transactional
    public void buscarSubstitutos(Long substituicaoId) {
        Substituicao s = substituicoes.findById(substituicaoId).orElse(null);
        if (s == null || s.getStatus() != StatusSubstituicao.AGUARDANDO_SUBSTITUTO) return;
        Contratacao original = s.getContratacaoOriginal();
        Vaga v = original.getVaga();
        Endereco local = v.getEndereco();
        double raio = s.getRaioKm().doubleValue();
        String cidade = raio <= 30 ? local.getCidade().toLowerCase() : null;
        String cepPrefixo = raio <= 5 ? local.getCep().substring(0, 5) + "%" : null;
        LocalDateTime agora = LocalDateTime.now();

        int avisados = 0;
        for (Freelancer f : freelancers.elegiveisParaSubstituicao(v.getFuncao().getId(), original.getFreelancer().getId(),
                agora, local.getUf(), cidade, cepPrefixo)) {
            if (contratacoes.temConflitoDeHorario(f.getId(), v.getInicio(), v.getFim(), null)) continue;
            if (jaFoiAvisado(f.getId(), s.getId())) continue;
            notificacoes.notificar(f.getId(), "SUBSTITUICAO", "Vaga relâmpago: " + v.getFuncao().getNome() + " em "
                    + v.getEmpresa().getNomeFantasia() + " (R$ " + original.getValorAcordado() + "). O primeiro a aceitar leva.", s.getId());
            avisados++;
        }
        if (avisados == 0 && notificacaoRepo.countByTipoAndReferenciaId("SUBSTITUICAO", s.getId()) == 0) {
            s.setStatus(StatusSubstituicao.SEM_CANDIDATOS);
            notificacoes.notificar(v.getEmpresa().getId(), "SUBSTITUICAO",
                    "Nenhum freelancer disponível no raio de " + s.getRaioKm() + " km. Aumente o raio para buscar de novo.", s.getId());
        }
    }

    private boolean jaFoiAvisado(Long freelancerId, Long substituicaoId) {
        return notificacaoRepo.existsByUsuarioIdAndTipoAndReferenciaId(freelancerId, "SUBSTITUICAO", substituicaoId);
    }

    @Transactional(readOnly = true)
    public Resposta consultar(Long id) {
        Substituicao s = carregar(id);
        Long logado = UsuarioAtual.id();
        boolean empresa = s.getContratacaoOriginal().getEmpresa().getId().equals(logado);
        boolean convidado = UsuarioAtual.perfil() == TipoUsuario.FREELANCER && jaFoiAvisado(logado, id);
        if (!empresa && !convidado && UsuarioAtual.perfil() != TipoUsuario.ADMINISTRADOR) {
            throw new AcessoNegadoException("Esta substituição é de outra empresa.");
        }
        return resposta(s);
    }

    /** UC13 4a: aumentar ou diminuir o raio e refazer a busca. */
    @Transactional
    public Resposta alterarRaio(Long id, RaioRequest req) {
        Substituicao s = daEmpresaLogada(id);
        if (!ABERTAS.contains(s.getStatus())) throw new ConflitoException("A substituição está " + s.getStatus() + ".");
        auditoria.registrar("substituicao", id, "ALTERACAO_RAIO", Map.of("raioKm", s.getRaioKm()), Map.of("raioKm", req.raioKm()));
        s.setRaioKm(req.raioKm());
        s.setStatus(StatusSubstituicao.AGUARDANDO_SUBSTITUTO);
        fila.enfileirar(TipoTarefa.BUSCAR_SUBSTITUTOS, Map.of("substituicaoId", s.getId()));
        return resposta(s);
    }

    @Transactional
    public Resposta cancelar(Long id) {
        Substituicao s = daEmpresaLogada(id);
        if (!ABERTAS.contains(s.getStatus())) throw new ConflitoException("A substituição está " + s.getStatus() + ".");
        s.setStatus(StatusSubstituicao.CANCELADA);
        auditoria.registrar("substituicao", id, "MUDANCA_STATUS", null, Map.of("status", "CANCELADA"));
        return resposta(s);
    }

    @Transactional(readOnly = true)
    public List<Resposta> convites() {
        return substituicoes.convitesAbertos(UsuarioAtual.id()).stream().map(this::resposta).toList();
    }

    /** "Primeiro a clicar leva": a linha da substituição fica travada; quem chega depois recebe 409. */
    @Transactional
    public Resposta aceitar(Long id) {
        Substituicao s = substituicoes.buscarComLock(id).orElseThrow(() -> new RecursoNaoEncontradoException("Substituição", id));
        Long freelancerId = UsuarioAtual.id();
        if (s.getStatus() == StatusSubstituicao.PREENCHIDA) throw new ConflitoException("Outro freelancer já aceitou esta vaga.");
        if (s.getStatus() != StatusSubstituicao.AGUARDANDO_SUBSTITUTO) throw new ConflitoException("Esta substituição não está mais aberta.");
        if (!jaFoiAvisado(freelancerId, id)) throw new AcessoNegadoException("Você não foi convidado para esta substituição.");

        Freelancer f = freelancers.findById(freelancerId).orElseThrow();
        Contas.exigirAtiva(f);
        Contratacao original = s.getContratacaoOriginal();
        Vaga v = original.getVaga();
        if (f.getFotoUrl() == null) throw new RegraNegocioException("Envie uma foto de perfil antes de aceitar um trabalho.");
        if (contratacoes.temConflitoDeHorario(f.getId(), v.getInicio(), v.getFim(), null)) {
            throw new RegraNegocioException("Você já tem um turno que conflita com este horário.");
        }
        Candidatura cand = candidaturas.findByVagaIdAndFreelancerId(v.getId(), f.getId()).orElseGet(() -> {
            Candidatura nova = new Candidatura();
            nova.setVaga(v);
            nova.setFreelancer(f);
            return nova;
        });
        if (cand.getId() != null && contratacoes.findByCandidaturaId(cand.getId()).isPresent()) {
            throw new ConflitoException("Você já tem uma contratação nesta vaga.");
        }
        cand.setStatus(StatusCandidatura.CONTRATADA);
        candidaturas.save(cand);

        Contratacao nova = new Contratacao();
        nova.setCandidatura(cand);
        nova.setValorAcordado(original.getValorAcordado());
        contratacoes.save(nova);
        pagamentoService.garantirPagamento(nova); // cobrança do substituto em custódia
        nova.setStatus(StatusContratacao.CONFIRMADA);
        nova.setConfirmadaEm(LocalDateTime.now());

        // A original sai de cena. Sem check-in, o valor dela volta para a empresa.
        if (original.getStatus() == StatusContratacao.CONFIRMADA) {
            pagamentoService.estornarSeHouver(original, "Freelancer substituído antes do turno");
        }
        if (original.getStatus() != StatusContratacao.NO_SHOW) {
            original.setStatus(StatusContratacao.SUBSTITUIDA);
            if (original.getEncerradaEm() == null) original.setEncerradaEm(LocalDateTime.now());
            original.setMotivoEncerramento("Substituído (" + s.getMotivo() + ")");
        }
        s.setNovaContratacao(nova);
        s.setStatus(StatusSubstituicao.PREENCHIDA);
        auditoria.registrar("substituicao", id, "MUDANCA_STATUS", Map.of("status", "AGUARDANDO_SUBSTITUTO"),
                Map.of("status", "PREENCHIDA", "novaContratacaoId", nova.getId()));
        notificacoes.notificar(v.getEmpresa().getId(), "SUBSTITUICAO",
                f.getNome() + " aceitou a substituição do turno de " + v.getFuncao().getNome() + ".", s.getId());
        return resposta(s);
    }

    // ------------------------------------------------------------------ auxiliares

    private Substituicao carregar(Long id) {
        return substituicoes.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Substituição", id));
    }

    private Substituicao daEmpresaLogada(Long id) {
        Substituicao s = carregar(id);
        if (!s.getContratacaoOriginal().getEmpresa().getId().equals(UsuarioAtual.id())) {
            throw new AcessoNegadoException("Esta substituição é de outra empresa.");
        }
        return s;
    }

    private Resposta resposta(Substituicao s) {
        return Resposta.de(s, notificacaoRepo.countByTipoAndReferenciaId("SUBSTITUICAO", s.getId()));
    }
}
