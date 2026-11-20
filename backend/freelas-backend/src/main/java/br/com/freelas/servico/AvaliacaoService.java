package br.com.freelas.servico;

import br.com.freelas.config.PropriedadesFreelas;
import br.com.freelas.dto.AvaliacaoDto.*;
import br.com.freelas.dto.PaginaResposta;
import br.com.freelas.entidade.*;
import br.com.freelas.enums.*;
import br.com.freelas.excecao.*;
import br.com.freelas.repositorio.*;
import br.com.freelas.seguranca.UsuarioAtual;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Avaliação mútua e cega (Avaliação e reputação): cada lado avalia em até 72 h depois do turno;
 * as notas só aparecem quando os dois avaliam ou quando o prazo acaba. Contestação em até 5 dias úteis.
 */
@Service
public class AvaliacaoService {

    /** Filtro simples de linguagem imprópria (UC12 4b). Em produção, usar um serviço de moderação. */
    private static final Set<String> PALAVRAS_PROIBIDAS = Set.of("idiota", "imbecil", "otario", "lixo", "vagabundo", "burro");

    private final AvaliacaoRepository avaliacoes;
    private final ContestacaoRepository contestacoes;
    private final ContratacaoRepository contratacoes;
    private final OcorrenciaRepository ocorrencias;
    private final UsuarioRepository usuarios;
    private final ReputacaoService reputacao;
    private final FreelancerService freelancerService;
    private final NotificacaoService notificacoes;
    private final AuditoriaService auditoria;
    private final PropriedadesFreelas props;

    public AvaliacaoService(AvaliacaoRepository avaliacoes, ContestacaoRepository contestacoes,
                            ContratacaoRepository contratacoes, OcorrenciaRepository ocorrencias,
                            UsuarioRepository usuarios, ReputacaoService reputacao, FreelancerService freelancerService,
                            NotificacaoService notificacoes, AuditoriaService auditoria, PropriedadesFreelas props) {
        this.avaliacoes = avaliacoes;
        this.contestacoes = contestacoes;
        this.contratacoes = contratacoes;
        this.ocorrencias = ocorrencias;
        this.usuarios = usuarios;
        this.reputacao = reputacao;
        this.freelancerService = freelancerService;
        this.notificacoes = notificacoes;
        this.auditoria = auditoria;
        this.props = props;
    }

    @Transactional
    public Resposta avaliar(Long contratacaoId, Request req) {
        Contratacao c = contratacoes.findById(contratacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Contratação", contratacaoId));
        Long logado = UsuarioAtual.id();
        DirecaoAvaliacao direcao;
        Usuario avaliado;
        if (c.getEmpresa().getId().equals(logado)) {
            direcao = DirecaoAvaliacao.EMPRESA_P_FREELA;
            avaliado = c.getFreelancer();
        } else if (c.getFreelancer().getId().equals(logado)) {
            direcao = DirecaoAvaliacao.FREELA_P_EMPRESA;
            avaliado = c.getEmpresa();
        } else {
            throw new AcessoNegadoException("Só quem participou do turno pode avaliar.");
        }
        if (c.getStatus() != StatusContratacao.CONCLUIDA) {
            throw new RegraNegocioException("Só é possível avaliar depois que o turno for CONCLUIDO.");
        }
        LocalDateTime prazo = c.getEncerradaEm().plusHours(props.regras().janelaAvaliacaoHoras());
        if (LocalDateTime.now().isAfter(prazo)) {
            throw new RegraNegocioException("O prazo de " + props.regras().janelaAvaliacaoHoras() + " h para avaliar terminou.");
        }
        if (avaliacoes.existsByContratacaoIdAndDirecao(contratacaoId, direcao)) {
            throw new ConflitoException("Você já avaliou este turno. Depois de enviada, a avaliação não muda.");
        }
        if (req.comentario() != null && contemPalavraProibida(req.comentario())) {
            throw new RegraNegocioException("O comentário tem conteúdo impróprio. Revise o texto e envie de novo.");
        }
        Avaliacao a = new Avaliacao();
        a.setContratacao(c);
        a.setAvaliador(usuarios.getReferenceById(logado));
        a.setAvaliado(avaliado);
        a.setDirecao(direcao);
        a.setNota(req.nota());
        a.setComentario(req.comentario());
        if (req.criterios() != null) {
            Set<String> vistos = new HashSet<>();
            for (CriterioRequest cr : req.criterios()) {
                String nome = cr.criterio().trim().toUpperCase();
                if (!vistos.add(nome)) throw new RegraNegocioException("Critério " + nome + " repetido.");
                AvaliacaoCriterio ac = new AvaliacaoCriterio();
                ac.setAvaliacao(a);
                ac.getId().setCriterio(nome);
                ac.setNota(cr.nota());
                a.getCriterios().add(ac);
            }
        }
        avaliacoes.save(a);

        // Avaliação cega: quando os dois lados avaliaram, as duas aparecem
        DirecaoAvaliacao outra = direcao == DirecaoAvaliacao.EMPRESA_P_FREELA ? DirecaoAvaliacao.FREELA_P_EMPRESA : DirecaoAvaliacao.EMPRESA_P_FREELA;
        avaliacoes.findByContratacaoIdAndDirecao(contratacaoId, outra).ifPresent(o -> {
            o.setVisivel(true);
            a.setVisivel(true);
            avaliacoes.flush();
            reputacao.recalcular(o.getAvaliado().getId());
            reputacao.recalcular(a.getAvaliado().getId());
        });
        notificacoes.notificar(avaliado.getId(), "AVALIACAO", "Você recebeu uma avaliação do turno " + contratacaoId + ".", a.getId());
        return Resposta.de(a, true);
    }

    @Transactional(readOnly = true)
    public List<Resposta> daContratacao(Long contratacaoId) {
        Contratacao c = contratacoes.findById(contratacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Contratação", contratacaoId));
        Long logado = UsuarioAtual.id();
        boolean admin = UsuarioAtual.perfil() == TipoUsuario.ADMINISTRADOR;
        if (!admin && !c.getEmpresa().getId().equals(logado) && !c.getFreelancer().getId().equals(logado)) {
            throw new AcessoNegadoException("Esta contratação é de outra conta.");
        }
        return avaliacoes.findByContratacaoId(contratacaoId).stream()
                .map(a -> Resposta.de(a, admin || a.getVisivel() || a.getAvaliador().getId().equals(logado)))
                .toList();
    }

    /** Avaliações publicadas recebidas por uma empresa (KAN-63 RN13), só leitura. */
    @Transactional(readOnly = true)
    public PaginaResposta<Resposta> recebidasPorEmpresa(Long empresaId, int pagina, int tamanho) {
        return recebidas(empresaId, pagina, tamanho);
    }

    @Transactional(readOnly = true)
    public PaginaResposta<Resposta> recebidasPorFreelancer(Long freelancerId, int pagina, int tamanho) {
        freelancerService.exigirPodeVer(freelancerId);
        return recebidas(freelancerId, pagina, tamanho);
    }

    private PaginaResposta<Resposta> recebidas(Long usuarioId, int pagina, int tamanho) {
        var page = avaliacoes.listarVisiveisRecebidas(usuarioId, Paginas.de(pagina, tamanho, Sort.by(Sort.Direction.DESC, "enviadaEm")));
        return PaginaResposta.de(page, a -> Resposta.de(a, true));
    }

    @Transactional(readOnly = true)
    public List<PendenteResposta> pendentesDoFreelancer() {
        int janela = props.regras().janelaAvaliacaoHoras();
        return contratacoes.pendentesDeAvaliacaoDoFreelancer(UsuarioAtual.id(), LocalDateTime.now().minusHours(janela)).stream()
                .map(c -> new PendenteResposta(c.getId(), c.getEmpresa().getNomeFantasia(), c.getVaga().getFuncao().getNome(),
                        c.getEncerradaEm(), c.getEncerradaEm().plusHours(janela)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ScoreResposta scoreDoFreelancer() {
        Freelancer f = freelancerService.logado();
        LocalDateTime desde = LocalDateTime.now().minusDays(ReputacaoService.JANELA_DIAS);
        Double media = avaliacoes.mediaVisivelDesde(f.getId(), desde);
        BigDecimal penalidade = ocorrencias.somarImpactoDesde(f.getId(), desde);
        var lista = ocorrencias.findByUsuarioIdAndRegistradaEmAfterOrderByRegistradaEmDesc(f.getId(), desde).stream()
                .map(o -> new OcorrenciaResumo(o.getTipo(), o.getImpactoScore(), o.getRegistradaEm(), o.getContratacao().getId()))
                .toList();
        return new ScoreResposta(f.getScore(), f.getNivelReputacao(),
                media == null ? null : BigDecimal.valueOf(media).setScale(2, RoundingMode.HALF_UP), penalidade,
                contratacoes.countByCandidaturaFreelancerIdAndStatus(f.getId(), StatusContratacao.CONCLUIDA), lista,
                "score = média das notas visíveis dos últimos 90 dias - soma das penalidades das ocorrências do período (entre 0 e 5)");
    }

    /** Contestação em até 5 dias úteis, só por quem recebeu a avaliação. */
    @Transactional
    public ContestacaoResposta contestar(Long avaliacaoId, ContestacaoRequest req) {
        Avaliacao a = avaliacoes.findById(avaliacaoId).orElseThrow(() -> new RecursoNaoEncontradoException("Avaliação", avaliacaoId));
        if (!a.getAvaliado().getId().equals(UsuarioAtual.id())) throw new AcessoNegadoException("Só quem recebeu a avaliação pode contestar.");
        if (a.getStatus() == StatusAvaliacao.REMOVIDA) throw new RegraNegocioException("Esta avaliação já foi removida.");
        if (contestacoes.existsByAvaliacaoId(avaliacaoId)) throw new ConflitoException("Esta avaliação já tem uma contestação.");
        LocalDateTime limite = somarDiasUteis(a.getEnviadaEm(), props.regras().contestacaoDiasUteis());
        if (LocalDateTime.now().isAfter(limite)) {
            throw new RegraNegocioException("O prazo de " + props.regras().contestacaoDiasUteis() + " dias úteis para contestar terminou.");
        }
        Contestacao c = new Contestacao();
        c.setAvaliacao(a);
        c.setSolicitante(usuarios.getReferenceById(UsuarioAtual.id()));
        c.setJustificativa(req.justificativa());
        c.setEvidenciaUrl(req.evidenciaUrl());
        contestacoes.save(c);
        a.setStatus(StatusAvaliacao.CONTESTADA);
        auditoria.registrar("avaliacao", avaliacaoId, "CONTESTACAO", Map.of("status", "PUBLICADA"), Map.of("status", "CONTESTADA"));
        return ContestacaoResposta.de(c);
    }

    /** Mediação (Administrador): aceita = avaliação removida e score recalculado. */
    @Transactional
    public ContestacaoResposta decidirContestacao(Long contestacaoId, DecisaoContestacaoRequest req) {
        Contestacao c = contestacoes.findById(contestacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Contestação", contestacaoId));
        if (c.getStatus() != StatusContestacao.ABERTA) throw new ConflitoException("A contestação já foi decidida.");
        c.setStatus(req.aceita() ? StatusContestacao.ACEITA : StatusContestacao.NEGADA);
        c.setResolvidaEm(LocalDateTime.now());
        Avaliacao a = c.getAvaliacao();
        a.setStatus(req.aceita() ? StatusAvaliacao.REMOVIDA : StatusAvaliacao.PUBLICADA);
        avaliacoes.flush();
        reputacao.recalcular(a.getAvaliado().getId());
        notificacoes.notificar(c.getSolicitante().getId(), "AVALIACAO",
                "Sua contestação foi " + (req.aceita() ? "aceita: a avaliação foi removida." : "negada."), c.getId());
        auditoria.registrar("contestacao", contestacaoId, "DECISAO", null, Map.of("status", c.getStatus()));
        return ContestacaoResposta.de(c);
    }

    @Transactional(readOnly = true)
    public PaginaResposta<ContestacaoResposta> contestacoesAbertas(int pagina, int tamanho) {
        return PaginaResposta.de(contestacoes.findByStatus(StatusContestacao.ABERTA,
                Paginas.de(pagina, tamanho, Sort.by("abertaEm"))), ContestacaoResposta::de);
    }

    /** Tarefa agendada: fim da janela de 72 h deixa visível a avaliação de quem avaliou sozinho. */
    @Transactional
    public int publicarAvaliacoesVencidas() {
        var vencidas = avaliacoes.findByVisivelFalseAndEnviadaEmBefore(LocalDateTime.now().minusHours(props.regras().janelaAvaliacaoHoras()));
        vencidas.forEach(a -> a.setVisivel(true));
        avaliacoes.flush();
        vencidas.stream().map(a -> a.getAvaliado().getId()).distinct().forEach(reputacao::recalcular);
        return vencidas.size();
    }

    static LocalDateTime somarDiasUteis(LocalDateTime inicio, int dias) {
        LocalDateTime d = inicio;
        int somados = 0;
        while (somados < dias) {
            d = d.plusDays(1);
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) somados++;
        }
        return d;
    }

    static boolean contemPalavraProibida(String texto) {
        String normal = Normalizer.normalize(texto.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        for (String palavra : normal.split("[^a-z]+")) {
            if (PALAVRAS_PROIBIDAS.contains(palavra)) return true;
        }
        return false;
    }
}
