package br.com.freelas.servico;

import br.com.freelas.dto.CandidaturaDto.*;
import br.com.freelas.dto.PaginaResposta;
import br.com.freelas.entidade.*;
import br.com.freelas.enums.*;
import br.com.freelas.excecao.*;
import br.com.freelas.repositorio.*;
import br.com.freelas.seguranca.UsuarioAtual;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Candidaturas: o freelancer se candidata e a empresa avalia os candidatos (UC07, RF7, RF9, RF10). */
@Service
public class CandidaturaService {

    private final CandidaturaRepository candidaturas;
    private final ContratacaoRepository contratacoes;
    private final OcorrenciaRepository ocorrencias;
    private final FreelancerFuncaoRepository freelancerFuncoes;
    private final VagaService vagaService;
    private final FreelancerService freelancerService;
    private final NotificacaoService notificacoes;
    private final AuditoriaService auditoria;

    public CandidaturaService(CandidaturaRepository candidaturas, ContratacaoRepository contratacoes,
                              OcorrenciaRepository ocorrencias, FreelancerFuncaoRepository freelancerFuncoes,
                              VagaService vagaService, FreelancerService freelancerService,
                              NotificacaoService notificacoes, AuditoriaService auditoria) {
        this.candidaturas = candidaturas;
        this.contratacoes = contratacoes;
        this.ocorrencias = ocorrencias;
        this.freelancerFuncoes = freelancerFuncoes;
        this.vagaService = vagaService;
        this.freelancerService = freelancerService;
        this.notificacoes = notificacoes;
        this.auditoria = auditoria;
    }

    /**
     * Candidatar-se: vaga aberta, conta ativa, sem suspensão por no-show, perfil mínimo,
     * função compatível e sem conflito de horário (Endpoints Freelancer, seção 2).
     */
    @Transactional
    public Resposta candidatar(Long vagaId) {
        Freelancer f = freelancerService.logado();
        Contas.exigirAtiva(f);
        Vaga v = vagaService.carregar(vagaId);
        LocalDateTime agora = LocalDateTime.now();
        if (v.getStatus() != StatusVaga.PUBLICADA) {
            throw new RegraNegocioException("A vaga não está aberta para candidaturas (status " + v.getStatus() + ").");
        }
        if (!v.getInicio().isAfter(agora) || (v.getDisponivelAte() != null && v.getDisponivelAte().isBefore(agora))) {
            throw new RegraNegocioException("O prazo para se candidatar a esta vaga terminou.");
        }
        if (candidaturas.existsByVagaIdAndFreelancerId(vagaId, f.getId())) {
            throw new ConflitoException("Você já se candidatou a esta vaga.");
        }
        if (f.getSuspensoAte() != null && f.getSuspensoAte().isAfter(agora)) {
            throw new RegraNegocioException("Candidaturas suspensas até " + f.getSuspensoAte() + " por falta sem justificativa.");
        }
        if (f.getEndereco() == null) throw new RegraNegocioException("Complete o endereço no perfil antes de se candidatar.");
        if (!freelancerFuncoes.existsByFreelancerIdAndFuncaoId(f.getId(), v.getFuncao().getId())) {
            throw new RegraNegocioException("Adicione a função " + v.getFuncao().getNome() + " ao seu perfil para se candidatar.");
        }
        if (contratacoes.temConflitoDeHorario(f.getId(), v.getInicio(), v.getFim(), null)) {
            throw new RegraNegocioException("Você já tem um turno que conflita com este horário.");
        }
        Candidatura c = new Candidatura();
        c.setVaga(v);
        c.setFreelancer(f);
        candidaturas.save(c);
        notificacoes.notificar(v.getEmpresa().getId(), "CANDIDATURA",
                f.getNome() + " se candidatou à vaga de " + v.getFuncao().getNome() + ".", c.getId());
        return Resposta.de(c, null);
    }

    @Transactional(readOnly = true)
    public PaginaResposta<Resposta> minhas(StatusCandidatura status, int pagina, int tamanho) {
        Long id = UsuarioAtual.id();
        var pageable = Paginas.de(pagina, tamanho, Sort.by(Sort.Direction.DESC, "criadaEm"));
        var page = status == null ? candidaturas.findByFreelancerId(id, pageable)
                : candidaturas.findByFreelancerIdAndStatus(id, status, pageable);
        return PaginaResposta.de(page, this::resposta);
    }

    @Transactional(readOnly = true)
    public Resposta buscar(Long id) {
        return resposta(carregarParticipante(id));
    }

    /** Desistir antes da contratação ser confirmada. */
    @Transactional
    public Resposta cancelar(Long id) {
        Candidatura c = carregar(id);
        if (!c.getFreelancer().getId().equals(UsuarioAtual.id())) throw new AcessoNegadoException("Esta candidatura é de outro freelancer.");
        if (c.getStatus() != StatusCandidatura.ENVIADA) {
            throw new ConflitoException(c.getStatus() == StatusCandidatura.SELECIONADA
                    ? "Você já foi selecionado: recuse a contratação em vez de cancelar a candidatura."
                    : "A candidatura está " + c.getStatus() + " e não pode ser cancelada.");
        }
        c.setStatus(StatusCandidatura.DESISTENCIA);
        auditoria.registrar("candidatura", id, "MUDANCA_STATUS", Map.of("status", "ENVIADA"), Map.of("status", "DESISTENCIA"));
        return resposta(c);
    }

    /** PATCH /candidaturas/{id}: a empresa recusa o candidato. */
    @Transactional
    public Resposta atualizarPelaEmpresa(Long id, AtualizacaoRequest req) {
        Candidatura c = carregar(id);
        vagaService.exigirDona(c.getVaga());
        if (req.status() != StatusCandidatura.RECUSADA) {
            throw new RegraNegocioException("A empresa só pode mudar a candidatura para RECUSADA. Para contratar use POST /vagas/{id}/contratacoes.");
        }
        if (c.getStatus() != StatusCandidatura.ENVIADA) {
            throw new ConflitoException("A candidatura está " + c.getStatus() + " e não pode ser recusada.");
        }
        c.setStatus(StatusCandidatura.RECUSADA);
        notificacoes.notificar(c.getFreelancer().getId(), "CANDIDATURA",
                "Sua candidatura para " + c.getVaga().getFuncao().getNome() + " não foi selecionada.", c.getId());
        auditoria.registrar("candidatura", id, "MUDANCA_STATUS", Map.of("status", "ENVIADA"), Map.of("status", "RECUSADA"));
        return resposta(c);
    }

    /** UC07: candidatos da vaga ordenados por score, trabalhos concluídos ou data. */
    @Transactional(readOnly = true)
    public PaginaResposta<CandidatoResposta> candidatosDaVaga(Long vagaId, Ordenacao ordenarPor, Sort.Direction ordem,
                                                             int pagina, int tamanho) {
        Vaga v = vagaService.daEmpresaLogada(vagaId);
        LocalDateTime trintaDias = LocalDateTime.now().minusDays(30);
        List<CandidatoResposta> lista = candidaturas.listarVisiveisDaVaga(v.getId()).stream()
                .map(c -> CandidatoResposta.de(c,
                        contratacoes.countByCandidaturaFreelancerIdAndStatus(c.getFreelancer().getId(), StatusContratacao.CONCLUIDA),
                        ocorrencias.existsByUsuarioIdAndRegistradaEmAfter(c.getFreelancer().getId(), trintaDias)))
                .toList();
        Ordenacao campo = ordenarPor == null ? Ordenacao.DATA_CANDIDATURA : ordenarPor;
        Comparator<CandidatoResposta> comparador = switch (campo) {
            case SCORE -> Comparator.comparing(CandidatoResposta::score);
            case TRABALHOS_CONCLUIDOS -> Comparator.comparingLong(CandidatoResposta::trabalhosConcluidos);
            case DATA_CANDIDATURA -> Comparator.comparing(CandidatoResposta::criadaEm);
        };
        Sort.Direction direcao = ordem != null ? ordem : (campo == Ordenacao.DATA_CANDIDATURA ? Sort.Direction.ASC : Sort.Direction.DESC);
        if (direcao == Sort.Direction.DESC) comparador = comparador.reversed();
        return PaginaResposta.deLista(lista.stream().sorted(comparador).toList(), pagina, tamanho);
    }

    // ------------------------------------------------------------------ auxiliares

    private Candidatura carregar(Long id) {
        return candidaturas.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Candidatura", id));
    }

    private Candidatura carregarParticipante(Long id) {
        Candidatura c = carregar(id);
        Long logado = UsuarioAtual.id();
        if (!c.getFreelancer().getId().equals(logado) && !c.getVaga().getEmpresa().getId().equals(logado)
                && UsuarioAtual.perfil() != TipoUsuario.ADMINISTRADOR) {
            throw new AcessoNegadoException("Esta candidatura é de outra conta.");
        }
        return c;
    }

    private Resposta resposta(Candidatura c) {
        return Resposta.de(c, contratacoes.findByCandidaturaId(c.getId()).map(Contratacao::getId).orElse(null));
    }
}
