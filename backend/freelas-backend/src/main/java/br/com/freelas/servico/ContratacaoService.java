package br.com.freelas.servico;

import br.com.freelas.config.PropriedadesFreelas;
import br.com.freelas.dto.ContratacaoDto.*;
import br.com.freelas.dto.PaginaResposta;
import br.com.freelas.entidade.*;
import br.com.freelas.enums.*;
import br.com.freelas.excecao.*;
import br.com.freelas.repositorio.*;
import br.com.freelas.seguranca.UsuarioAtual;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * O turno, da seleção à conclusão (UC09; KAN-70 RN05 a RN17):
 * AGUARDANDO_ACEITE -> (aceite + cobrança) CONFIRMADA -> (check-in) EM_ANDAMENTO
 * -> (check-out + confirmação ou 6 h) CONCLUIDA. Desvios: CANCELADA, NO_SHOW, SUBSTITUIDA.
 */
@Service
public class ContratacaoService {

    private final ContratacaoRepository contratacoes;
    private final CandidaturaRepository candidaturas;
    private final VagaRepository vagas;
    private final PagamentoRepository pagamentos;
    private final MeioPagamentoEmpresaRepository meiosPagamento;
    private final OcorrenciaRepository ocorrencias;
    private final VagaService vagaService;
    private final PagamentoService pagamentoService;
    private final ReputacaoService reputacao;
    private final SubstituicaoService substituicaoService;
    private final NotificacaoService notificacoes;
    private final AuditoriaService auditoria;
    private final PropriedadesFreelas props;

    public ContratacaoService(ContratacaoRepository contratacoes, CandidaturaRepository candidaturas,
                              VagaRepository vagas, PagamentoRepository pagamentos,
                              MeioPagamentoEmpresaRepository meiosPagamento, OcorrenciaRepository ocorrencias,
                              VagaService vagaService, PagamentoService pagamentoService, ReputacaoService reputacao,
                              SubstituicaoService substituicaoService, NotificacaoService notificacoes,
                              AuditoriaService auditoria, PropriedadesFreelas props) {
        this.contratacoes = contratacoes;
        this.candidaturas = candidaturas;
        this.vagas = vagas;
        this.pagamentos = pagamentos;
        this.meiosPagamento = meiosPagamento;
        this.ocorrencias = ocorrencias;
        this.vagaService = vagaService;
        this.pagamentoService = pagamentoService;
        this.reputacao = reputacao;
        this.substituicaoService = substituicaoService;
        this.notificacoes = notificacoes;
        this.auditoria = auditoria;
        this.props = props;
    }

    // ------------------------------------------------------------------ empresa seleciona

    /** UC09: a vaga fica travada (FOR UPDATE) para não passar da quantidade de profissionais. */
    @Transactional
    public Resposta contratar(Long vagaId, ContratarRequest req) {
        Vaga v = vagas.buscarComLock(vagaId).orElseThrow(() -> new RecursoNaoEncontradoException("Vaga", vagaId));
        vagaService.exigirDona(v);
        Contas.exigirAtiva(v.getEmpresa());
        Candidatura cand = candidaturas.findById(req.candidaturaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Candidatura", req.candidaturaId()));
        if (!cand.getVaga().getId().equals(vagaId)) {
            throw new RegraNegocioException("A candidatura " + req.candidaturaId() + " não é desta vaga (KAN-70 RN05).");
        }
        if (cand.getStatus() != StatusCandidatura.ENVIADA) {
            throw new ConflitoException("A candidatura não está mais disponível (status " + cand.getStatus() + ").");
        }
        if (v.getStatus() == StatusVaga.PREENCHIDA) throw new ConflitoException("Todas as posições da vaga já foram preenchidas.");
        if (v.getStatus() != StatusVaga.PUBLICADA) throw new RegraNegocioException("A vaga está " + v.getStatus() + ".");
        long ocupadas = contratacoes.countByCandidaturaVagaIdAndStatusIn(vagaId, VagaService.OCUPAM_POSICAO);
        if (ocupadas >= v.getQuantidadeProfissionais()) {
            throw new ConflitoException("Todas as " + v.getQuantidadeProfissionais() + " posições já têm profissional selecionado.");
        }
        if (!meiosPagamento.existsByEmpresaIdAndAtivoTrue(v.getEmpresa().getId())) {
            throw new RegraNegocioException("Cadastre um meio de pagamento ativo antes de contratar (KAN-70 RN09).");
        }
        Contratacao c = new Contratacao();
        c.setCandidatura(cand);
        c.setValorAcordado(VagaService.valorTotal(v));
        contratacoes.save(c);
        cand.setStatus(StatusCandidatura.SELECIONADA);
        notificacoes.notificar(cand.getFreelancer().getId(), "CONTRATACAO",
                "Você foi selecionado para " + v.getFuncao().getNome() + " em " + v.getEmpresa().getNomeFantasia()
                        + ". Aceite pelo app para confirmar.", c.getId());
        auditoria.registrar("contratacao", c.getId(), "CRIACAO", null, Map.of("status", "AGUARDANDO_ACEITE"));
        return resposta(c);
    }

    // ------------------------------------------------------------------ freelancer aceita ou recusa

    /** Aceite: dispara a cobrança em custódia e só então confirma (decisão do grupo, KAN-70 RN08). */
    @Transactional
    public Resposta aceitar(Long id) {
        Contratacao c = doFreelancerLogado(id);
        Freelancer f = c.getFreelancer();
        Contas.exigirAtiva(f);
        if (c.getStatus() != StatusContratacao.AGUARDANDO_ACEITE) {
            throw new ConflitoException("A contratação está " + c.getStatus() + " e não aguarda aceite.");
        }
        Vaga v = vagas.buscarComLock(c.getVaga().getId()).orElseThrow();
        if (!v.getInicio().isAfter(LocalDateTime.now())) throw new RegraNegocioException("O turno já começou; o prazo de aceite terminou.");
        if (f.getFotoUrl() == null) throw new RegraNegocioException("Envie uma foto de perfil antes de aceitar um trabalho.");
        if (contratacoes.temConflitoDeHorario(f.getId(), v.getInicio(), v.getFim(), c.getId())) {
            throw new RegraNegocioException("Você já tem um turno que conflita com este horário.");
        }
        long confirmadas = contratacoes.countByCandidaturaVagaIdAndStatusIn(v.getId(), VagaService.CONFIRMADAS);
        if (confirmadas >= v.getQuantidadeProfissionais()) throw new ConflitoException("A vaga já foi preenchida.");

        pagamentoService.garantirPagamento(c); // 422 se o gateway recusar

        c.setStatus(StatusContratacao.CONFIRMADA);
        c.setConfirmadaEm(LocalDateTime.now());
        c.getCandidatura().setStatus(StatusCandidatura.CONTRATADA);
        if (confirmadas + 1 >= v.getQuantidadeProfissionais() && v.getStatus() == StatusVaga.PUBLICADA) {
            v.setStatus(StatusVaga.PREENCHIDA);
        }
        notificacoes.notificar(v.getEmpresa().getId(), "CONTRATACAO",
                f.getNome() + " aceitou o turno de " + v.getFuncao().getNome() + ".", c.getId());
        auditoria.registrar("contratacao", id, "MUDANCA_STATUS", Map.of("status", "AGUARDANDO_ACEITE"), Map.of("status", "CONFIRMADA"));
        return resposta(c);
    }

    @Transactional
    public Resposta recusar(Long id) {
        Contratacao c = doFreelancerLogado(id);
        if (c.getStatus() != StatusContratacao.AGUARDANDO_ACEITE) {
            throw new ConflitoException("A contratação está " + c.getStatus() + " e não aguarda aceite.");
        }
        encerrar(c, StatusContratacao.CANCELADA, c.getFreelancer(), "Seleção recusada pelo freelancer");
        c.getCandidatura().setStatus(StatusCandidatura.DESISTENCIA);
        pagamentoService.estornarSeHouver(c, "Freelancer recusou o novo aceite");
        reabrirVagaSePreciso(c.getVaga());
        notificacoes.notificar(c.getEmpresa().getId(), "CONTRATACAO",
                c.getFreelancer().getNome() + " recusou a vaga de " + c.getVaga().getFuncao().getNome() + ".", c.getId());
        return resposta(c);
    }

    // ------------------------------------------------------------------ execução do turno

    @Transactional
    public Resposta checkin(Long id, CheckinRequest req) {
        Contratacao c = doFreelancerLogado(id);
        if (c.getStatus() != StatusContratacao.CONFIRMADA) {
            throw new ConflitoException("Check-in só para contratação CONFIRMADA (status atual: " + c.getStatus() + ").");
        }
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime liberaEm = c.getVaga().getInicio().minusMinutes(props.regras().checkinAntecedenciaMinutos());
        if (agora.isBefore(liberaEm)) throw new RegraNegocioException("O check-in abre às " + liberaEm + " (UTC).");
        if (agora.isAfter(c.getVaga().getFim())) throw new RegraNegocioException("O horário do turno já terminou.");
        c.setStatus(StatusContratacao.EM_ANDAMENTO);
        c.setCheckinEm(agora);
        auditoria.registrar("contratacao", id, "CHECKIN", Map.of("status", "CONFIRMADA"),
                Map.of("status", "EM_ANDAMENTO", "latitude", String.valueOf(req == null ? null : req.latitude()),
                        "longitude", String.valueOf(req == null ? null : req.longitude())));
        notificacoes.notificar(c.getEmpresa().getId(), "CONTRATACAO", c.getFreelancer().getNome() + " fez check-in.", id);
        return resposta(c);
    }

    /** Check-out: começa a janela de 6 h para a empresa confirmar ou contestar. */
    @Transactional
    public Resposta checkout(Long id, CheckinRequest req) {
        Contratacao c = doFreelancerLogado(id);
        if (c.getStatus() != StatusContratacao.EM_ANDAMENTO || c.getCheckoutEm() != null) {
            throw new ConflitoException("Check-out só depois do check-in e uma única vez.");
        }
        c.setCheckoutEm(LocalDateTime.now());
        pagamentoService.aguardarConfirmacao(c);
        auditoria.registrar("contratacao", id, "CHECKOUT", null, Map.of("checkoutEm", c.getCheckoutEm()));
        notificacoes.notificar(c.getEmpresa().getId(), "CONTRATACAO", c.getFreelancer().getNome()
                + " fez check-out. Confirme em até " + props.regras().janelaConfirmacaoPagamentoHoras()
                + " h ou o pagamento é liberado automaticamente.", id);
        return resposta(c);
    }

    /** A empresa confirma o fim do turno: CONCLUIDA e pagamento liberado. */
    @Transactional
    public Resposta concluir(Long id) {
        Contratacao c = daEmpresaLogada(id);
        if (c.getStatus() == StatusContratacao.CONCLUIDA) throw new ConflitoException("A contratação já está concluída.");
        if (c.getCheckoutEm() == null) throw new RegraNegocioException("O freelancer ainda não fez check-out.");
        pagamentoService.liberarDaContratacao(c);
        concluirInterno(c);
        return resposta(c);
    }

    /** Chamado pelo worker quando as 6 h acabam sem confirmação nem disputa. */
    @Transactional
    public void liberarAutomaticamente(Long pagamentoId) {
        pagamentos.findById(pagamentoId).ifPresent(p -> {
            if (p.getStatus() != StatusPagamento.AGUARDANDO_CONFIRMACAO) return;
            pagamentoService.liberar(p);
            if (p.getContratacao().getStatus() == StatusContratacao.EM_ANDAMENTO) concluirInterno(p.getContratacao());
        });
    }

    private void concluirInterno(Contratacao c) {
        StatusContratacao anterior = c.getStatus();
        c.setStatus(StatusContratacao.CONCLUIDA);
        c.setEncerradaEm(LocalDateTime.now());
        auditoria.registrar("contratacao", c.getId(), "MUDANCA_STATUS", Map.of("status", anterior), Map.of("status", "CONCLUIDA"));
        notificacoes.notificar(c.getFreelancer().getId(), "AVALIACAO",
                "Turno concluído. Você tem " + props.regras().janelaAvaliacaoHoras() + " h para avaliar a empresa.", c.getId());
        notificacoes.notificar(c.getEmpresa().getId(), "AVALIACAO",
                "Turno concluído. Você tem " + props.regras().janelaAvaliacaoHoras() + " h para avaliar o freelancer.", c.getId());
    }

    // ------------------------------------------------------------------ cancelamento e ocorrências

    /**
     * Cancelamento antes do check-in, pela empresa ou pelo freelancer (KAN-70 RN11, RN12, RN14, RN16).
     * Freelancer: penalidade conforme a antecedência e reposição automática. Empresa: estorno.
     */
    @Transactional
    public Resposta cancelar(Long id, CancelamentoRequest req) {
        Contratacao c = carregar(id);
        Long logado = UsuarioAtual.id();
        boolean ehEmpresa = c.getEmpresa().getId().equals(logado);
        boolean ehFreelancer = c.getFreelancer().getId().equals(logado);
        if (!ehEmpresa && !ehFreelancer) throw new AcessoNegadoException("Esta contratação é de outra conta.");
        if (c.getStatus() != StatusContratacao.AGUARDANDO_ACEITE && c.getStatus() != StatusContratacao.CONFIRMADA) {
            throw new RegraNegocioException("Só dá para cancelar antes do check-in. Com o turno em andamento, use a substituição ou registre uma ocorrência.");
        }
        boolean estavaConfirmada = c.getStatus() == StatusContratacao.CONFIRMADA;
        Usuario quem = ehEmpresa ? c.getEmpresa() : c.getFreelancer();
        encerrar(c, StatusContratacao.CANCELADA, quem, req.motivo());
        pagamentoService.estornarSeHouver(c, ehEmpresa ? "Cancelado pela empresa" : "Cancelado pelo freelancer");

        if (ehFreelancer) {
            c.getCandidatura().setStatus(StatusCandidatura.DESISTENCIA);
            if (estavaConfirmada) {
                long horas = Duration.between(LocalDateTime.now(), c.getVaga().getInicio()).toHours();
                TipoOcorrencia tipo = horas >= props.regras().cancelamentoAntecedenciaHoras()
                        ? TipoOcorrencia.CANCELAMENTO_PREVIO : TipoOcorrencia.CANCELAMENTO_TARDIO;
                reputacao.registrarOcorrencia(c, c.getFreelancer(), tipo);
                // Reposição automática (Solicitação de substituição)
                substituicaoService.criarAutomatica(c, MotivoSubstituicao.CANCELAMENTO);
            } else {
                reabrirVagaSePreciso(c.getVaga());
            }
            notificacoes.notificar(c.getEmpresa().getId(), "CONTRATACAO", c.getFreelancer().getNome()
                    + " cancelou o turno de " + c.getVaga().getFuncao().getNome()
                    + (estavaConfirmada ? ". A busca por substituto começou." : "."), id);
        } else {
            c.getCandidatura().setStatus(StatusCandidatura.RECUSADA);
            reabrirVagaSePreciso(c.getVaga());
            notificacoes.notificar(c.getFreelancer().getId(), "CONTRATACAO",
                    "A empresa cancelou o turno de " + c.getVaga().getFuncao().getNome() + ": " + req.motivo(), id);
        }
        return resposta(c);
    }

    /** A empresa registra no-show, cancelamento tardio ou abandono do turno (KAN-70 RN16). */
    @Transactional
    public OcorrenciaResposta registrarOcorrencia(Long id, OcorrenciaRequest req) {
        Contratacao c = daEmpresaLogada(id);
        LocalDateTime agora = LocalDateTime.now();
        if (ocorrencias.findByUsuarioIdAndRegistradaEmAfterOrderByRegistradaEmDesc(c.getFreelancer().getId(), agora.minusYears(1))
                .stream().anyMatch(o -> o.getContratacao().getId().equals(id) && o.getTipo() == req.tipo())) {
            throw new ConflitoException("Esta ocorrência já foi registrada para esta contratação.");
        }
        switch (req.tipo()) {
            case NO_SHOW -> {
                if (c.getStatus() != StatusContratacao.CONFIRMADA || agora.isBefore(c.getVaga().getInicio())) {
                    throw new RegraNegocioException("No-show só depois do início do turno e sem check-in.");
                }
                encerrar(c, StatusContratacao.NO_SHOW, null, "Freelancer não compareceu");
                c.getFreelancer().setSuspensoAte(agora.plusDays(props.regras().suspensaoNoShowDias()));
                pagamentoService.estornarSeHouver(c, "No-show do freelancer");
                notificacoes.notificar(c.getFreelancer().getId(), "OCORRENCIA", "Falta registrada no turno "
                        + id + ". Novas candidaturas suspensas por " + props.regras().suspensaoNoShowDias() + " dias.", id);
            }
            case ABANDONO_TURNO -> {
                if (c.getStatus() != StatusContratacao.EM_ANDAMENTO) {
                    throw new RegraNegocioException("Abandono só pode ser registrado com o turno em andamento.");
                }
                c.setMotivoEncerramento("Abandono do turno registrado pela empresa");
                notificacoes.notificar(c.getFreelancer().getId(), "OCORRENCIA", "A empresa registrou abandono do turno " + id + ".", id);
            }
            case CANCELAMENTO_TARDIO, CANCELAMENTO_PREVIO -> {
                if (c.getStatus() != StatusContratacao.CANCELADA) {
                    throw new RegraNegocioException("Ocorrência de cancelamento só para contratação CANCELADA.");
                }
            }
        }
        Ocorrencia o = reputacao.registrarOcorrencia(c, c.getFreelancer(), req.tipo());
        auditoria.registrar("contratacao", id, "OCORRENCIA", null, Map.of("tipo", req.tipo()));
        return OcorrenciaResposta.de(o);
    }

    // ------------------------------------------------------------------ consultas

    @Transactional(readOnly = true)
    public Resposta buscar(Long id) {
        Contratacao c = carregar(id);
        Long logado = UsuarioAtual.id();
        if (!c.getEmpresa().getId().equals(logado) && !c.getFreelancer().getId().equals(logado)
                && UsuarioAtual.perfil() != TipoUsuario.ADMINISTRADOR) {
            throw new AcessoNegadoException("Esta contratação é de outra conta.");
        }
        return resposta(c);
    }

    @Transactional(readOnly = true)
    public PaginaResposta<Resposta> listarDaEmpresa(Long empresaId, StatusContratacao status, Long vagaId, int pagina, int tamanho) {
        Contas.exigirMesmoUsuario(empresaId, UsuarioAtual.id());
        var page = contratacoes.listarDaEmpresa(empresaId, status, vagaId, Paginas.de(pagina, tamanho, Sort.by(Sort.Direction.DESC, "id")));
        return PaginaResposta.de(page, this::resposta);
    }

    @Transactional(readOnly = true)
    public PaginaResposta<Resposta> listarDoFreelancer(StatusContratacao status, int pagina, int tamanho) {
        var page = contratacoes.listarDoFreelancer(UsuarioAtual.id(), status, Paginas.de(pagina, tamanho, Sort.by(Sort.Direction.DESC, "id")));
        return PaginaResposta.de(page, this::resposta);
    }

    // ------------------------------------------------------------------ auxiliares

    public Contratacao carregar(Long id) {
        return contratacoes.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Contratação", id));
    }

    private Contratacao doFreelancerLogado(Long id) {
        Contratacao c = carregar(id);
        if (!c.getFreelancer().getId().equals(UsuarioAtual.id())) throw new AcessoNegadoException("Esta contratação é de outro freelancer.");
        return c;
    }

    private Contratacao daEmpresaLogada(Long id) {
        Contratacao c = carregar(id);
        if (!c.getEmpresa().getId().equals(UsuarioAtual.id())) throw new AcessoNegadoException("Esta contratação é de outra empresa.");
        return c;
    }

    private void encerrar(Contratacao c, StatusContratacao status, Usuario quem, String motivo) {
        StatusContratacao anterior = c.getStatus();
        c.setStatus(status);
        c.setCanceladaPor(quem);
        c.setEncerradaEm(LocalDateTime.now());
        c.setMotivoEncerramento(motivo);
        auditoria.registrar("contratacao", c.getId(), "MUDANCA_STATUS", Map.of("status", anterior),
                Map.of("status", status, "motivo", motivo));
    }

    /** Vaga PREENCHIDA volta a PUBLICADA quando uma posição abre antes do início. */
    private void reabrirVagaSePreciso(Vaga v) {
        if (v.getStatus() == StatusVaga.PREENCHIDA && v.getInicio().isAfter(LocalDateTime.now())) {
            v.setStatus(StatusVaga.PUBLICADA);
        }
    }

    public Resposta resposta(Contratacao c) {
        return Resposta.de(c, pagamentos.findByContratacaoId(c.getId()).map(Pagamento::getStatus).orElse(null));
    }

    /** Usado pela tarefa agendada: aceite que não veio antes do início do turno. */
    @Transactional
    public int expirarAceitesVencidos() {
        List<Contratacao> vencidas = contratacoes.aceitesVencidos(LocalDateTime.now());
        for (Contratacao c : vencidas) {
            encerrar(c, StatusContratacao.CANCELADA, null, "Prazo de aceite expirou");
            c.getCandidatura().setStatus(StatusCandidatura.DESISTENCIA);
            pagamentoService.estornarSeHouver(c, "Prazo de aceite expirou");
        }
        return vencidas.size();
    }
}
