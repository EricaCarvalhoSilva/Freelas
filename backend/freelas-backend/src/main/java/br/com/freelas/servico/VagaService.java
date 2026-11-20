package br.com.freelas.servico;

import br.com.freelas.config.PropriedadesFreelas;
import br.com.freelas.dto.PaginaResposta;
import br.com.freelas.dto.VagaDto.*;
import br.com.freelas.entidade.*;
import br.com.freelas.enums.*;
import br.com.freelas.excecao.AcessoNegadoException;
import br.com.freelas.excecao.ConflitoException;
import br.com.freelas.excecao.RecursoNaoEncontradoException;
import br.com.freelas.excecao.RegraNegocioException;
import br.com.freelas.fila.FilaService;
import br.com.freelas.repositorio.*;
import br.com.freelas.seguranca.UsuarioAtual;
import jakarta.persistence.criteria.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/** Vagas: criação, publicação, edição, cancelamento, busca e favoritos (UC05, UC06, UC11; KAN-69). */
@Service
public class VagaService {

    /** Contratações que ocupam uma posição da vaga. */
    public static final List<StatusContratacao> OCUPAM_POSICAO = List.of(StatusContratacao.AGUARDANDO_ACEITE,
            StatusContratacao.CONFIRMADA, StatusContratacao.EM_ANDAMENTO, StatusContratacao.CONCLUIDA);
    public static final List<StatusContratacao> CONFIRMADAS = List.of(StatusContratacao.CONFIRMADA,
            StatusContratacao.EM_ANDAMENTO, StatusContratacao.CONCLUIDA);
    private static final Set<StatusVaga> FINAIS = EnumSet.of(StatusVaga.CANCELADA, StatusVaga.CONCLUIDA, StatusVaga.ENCERRADA_PRAZO);

    private final VagaRepository vagas;
    private final FuncaoRepository funcoes;
    private final EnderecoRepository enderecos;
    private final MeioPagamentoEmpresaRepository meiosPagamento;
    private final ContratacaoRepository contratacoes;
    private final CandidaturaRepository candidaturas;
    private final ImpulsionamentoRepository impulsionamentos;
    private final VagaFavoritaRepository favoritas;
    private final LogAuditoriaRepository logs;
    private final EmpresaService empresaService;
    private final FreelancerService freelancerService;
    private final PagamentoService pagamentoService;
    private final NotificacaoService notificacoes;
    private final AuditoriaService auditoria;
    private final FilaService fila;
    private final PropriedadesFreelas props;
    private final JsonMapper jsonMapper;

    public VagaService(VagaRepository vagas, FuncaoRepository funcoes, EnderecoRepository enderecos,
                       MeioPagamentoEmpresaRepository meiosPagamento, ContratacaoRepository contratacoes,
                       CandidaturaRepository candidaturas, ImpulsionamentoRepository impulsionamentos,
                       VagaFavoritaRepository favoritas, LogAuditoriaRepository logs, EmpresaService empresaService,
                       FreelancerService freelancerService, PagamentoService pagamentoService,
                       NotificacaoService notificacoes, AuditoriaService auditoria, FilaService fila,
                       PropriedadesFreelas props, JsonMapper jsonMapper) {
        this.vagas = vagas;
        this.funcoes = funcoes;
        this.enderecos = enderecos;
        this.meiosPagamento = meiosPagamento;
        this.contratacoes = contratacoes;
        this.candidaturas = candidaturas;
        this.impulsionamentos = impulsionamentos;
        this.favoritas = favoritas;
        this.logs = logs;
        this.empresaService = empresaService;
        this.freelancerService = freelancerService;
        this.pagamentoService = pagamentoService;
        this.notificacoes = notificacoes;
        this.auditoria = auditoria;
        this.fila = fila;
        this.props = props;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(readOnly = true)
    public List<FuncaoResposta> listarFuncoes() {
        return funcoes.findAllByOrderByNomeAsc().stream().map(FuncaoResposta::de).toList();
    }

    // ------------------------------------------------------------------ empresa

    @Transactional
    public Resposta criar(CriarRequest req) {
        Empresa empresa = empresaService.logada();
        Contas.exigirAtiva(empresa);
        Vaga v = new Vaga();
        v.setEmpresa(empresa);
        v.setFuncao(funcao(req.funcaoId()));
        v.setEndereco(req.endereco() == null ? empresa.getEndereco() : enderecos.save(req.endereco().paraEntidade()));
        v.setDescricao(req.descricao());
        v.setQuantidadeProfissionais(req.quantidadeProfissionais().shortValue());
        v.setInicio(req.inicio());
        v.setFim(req.fim());
        v.setValor(req.valor());
        v.setTipoValor(req.tipoValor());
        v.setOfereceAlimentacao(req.ofereceAlimentacao());
        v.setTipoAlimentacao(req.tipoAlimentacao());
        v.setFormaPagamento(req.formaPagamento());
        v.setDisponivelAte(req.disponivelAte());
        vagas.save(v);
        auditoria.registrar("vaga", v.getId(), "CRIACAO", null, Map.of("status", v.getStatus(), "valor", v.getValor()));
        return resposta(v);
    }

    /** Valida as regras de publicação e lista todos os motivos de recusa (KAN-69 RN01 a RN06). */
    @Transactional
    public Resposta publicar(Long id) {
        Vaga v = daEmpresaLogada(id);
        if (v.getStatus() != StatusVaga.RASCUNHO) {
            throw new ConflitoException("Só vaga em RASCUNHO pode ser publicada (status atual: " + v.getStatus() + ").");
        }
        List<String> motivos = new ArrayList<>();
        Empresa empresa = v.getEmpresa();
        LocalDateTime agora = LocalDateTime.now();
        if (empresa.getStatus() != StatusUsuario.ATIVO) motivos.add("O cadastro da empresa não está ATIVO (confirme o e-mail).");
        BigDecimal minimo = valorMinimo(v.getTipoValor());
        if (v.getValor().compareTo(minimo) < 0) {
            motivos.add("Valor abaixo do mínimo da plataforma para " + v.getTipoValor() + ": R$ " + minimo + ".");
        }
        if (!v.getInicio().isAfter(agora)) motivos.add("O início do turno já passou.");
        if (!v.getFim().isAfter(v.getInicio())) motivos.add("O fim precisa ser depois do início.");
        if (v.getDisponivelAte() != null && v.getDisponivelAte().isBefore(agora)) motivos.add("O prazo de disponibilidade já passou.");
        if (!meiosPagamento.existsByEmpresaIdAndAtivoTrue(empresa.getId())) motivos.add("Cadastre um meio de pagamento ativo.");
        if (!motivos.isEmpty()) throw new RegraNegocioException("A vaga não pode ser publicada.", motivos);

        v.setStatus(StatusVaga.PUBLICADA);
        v.setPublicadaEm(agora);
        // Aviso em massa aos freelancers da função: vai para a fila (RNF15)
        fila.enfileirar(TipoTarefa.NOTIFICAR_NOVA_VAGA, Map.of("vagaId", v.getId()));
        auditoria.registrar("vaga", v.getId(), "MUDANCA_STATUS", Map.of("status", "RASCUNHO"), Map.of("status", "PUBLICADA"));
        return resposta(v);
    }

    @Transactional(readOnly = true)
    public PaginaResposta<Resposta> listarDaEmpresa(Long empresaId, StatusVaga status, int pagina, int tamanho) {
        Contas.exigirMesmoUsuario(empresaId, UsuarioAtual.id());
        var pageable = Paginas.de(pagina, tamanho, Sort.by(Sort.Direction.DESC, "inicio"));
        Page<Vaga> page = status == null ? vagas.findByEmpresaId(empresaId, pageable)
                : vagas.findByEmpresaIdAndStatus(empresaId, status, pageable);
        return PaginaResposta.de(page, this::resposta);
    }

    /** Empresa vê as próprias vagas; freelancer vê vagas que já saíram do rascunho. */
    @Transactional(readOnly = true)
    public Resposta buscar(Long id) {
        Vaga v = carregar(id);
        TipoUsuario perfil = UsuarioAtual.perfil();
        if (perfil == TipoUsuario.EMPRESA && !v.getEmpresa().getId().equals(UsuarioAtual.id())) {
            throw new AcessoNegadoException("Esta vaga é de outra empresa.");
        }
        if (perfil == TipoUsuario.FREELANCER && v.getStatus() == StatusVaga.RASCUNHO) {
            throw new RecursoNaoEncontradoException("Vaga", id);
        }
        return resposta(v);
    }

    /**
     * Edição da vaga (UC06, KAN-69 RN07 a RN11). Mudança que prejudica o freelancer confirmado
     * (reduzir valor, trocar função, horário ou local) devolve a contratação para novo aceite (KAN-70 RN15).
     */
    @Transactional
    public Resposta atualizar(Long id, AtualizarRequest req) {
        Vaga v = daEmpresaLogada(id);
        Contas.exigirAtiva(v.getEmpresa());
        if (FINAIS.contains(v.getStatus())) {
            throw new ConflitoException("Vaga " + v.getStatus() + " não pode ser editada. Crie outra vaga (KAN-69 RN15).");
        }
        List<Contratacao> confirmadas = contratacoes.findByCandidaturaVagaIdAndStatusIn(id,
                List.of(StatusContratacao.CONFIRMADA, StatusContratacao.AGUARDANDO_ACEITE));
        boolean temConfirmada = confirmadas.stream().anyMatch(c -> c.getStatus() == StatusContratacao.CONFIRMADA);
        Map<String, Object> antes = new LinkedHashMap<>();
        Map<String, Object> depois = new LinkedHashMap<>();
        boolean prejudica = false;

        if (req.funcaoId() != null && !req.funcaoId().equals(v.getFuncao().getId())) {
            antes.put("funcaoId", v.getFuncao().getId()); depois.put("funcaoId", req.funcaoId());
            v.setFuncao(funcao(req.funcaoId()));
            prejudica = true;
        }
        if (req.inicio() != null && !req.inicio().equals(v.getInicio())) {
            antes.put("inicio", v.getInicio()); depois.put("inicio", req.inicio());
            v.setInicio(req.inicio());
            prejudica = true;
        }
        if (req.fim() != null && !req.fim().equals(v.getFim())) {
            antes.put("fim", v.getFim()); depois.put("fim", req.fim());
            v.setFim(req.fim());
            prejudica = true;
        }
        if (!v.getFim().isAfter(v.getInicio())) throw new RegraNegocioException("O fim precisa ser depois do início.");
        if (req.valor() != null && req.valor().compareTo(v.getValor()) != 0) {
            if (req.valor().compareTo(v.getValor()) < 0) prejudica = true;
            antes.put("valor", v.getValor()); depois.put("valor", req.valor());
            v.setValor(req.valor());
        }
        if (req.tipoValor() != null && req.tipoValor() != v.getTipoValor()) {
            antes.put("tipoValor", v.getTipoValor()); depois.put("tipoValor", req.tipoValor());
            v.setTipoValor(req.tipoValor());
            prejudica = true;
        }
        if (req.endereco() != null) {
            antes.put("endereco", v.getEndereco().getCep()); depois.put("endereco", req.endereco().cep());
            v.setEndereco(enderecos.save(req.endereco().paraEntidade()));
            prejudica = true;
        }
        if (req.quantidadeProfissionais() != null) {
            long ocupadas = contratacoes.countByCandidaturaVagaIdAndStatusIn(id, OCUPAM_POSICAO);
            if (req.quantidadeProfissionais() < ocupadas) {
                throw new RegraNegocioException("Já há " + ocupadas + " profissionais selecionados; a quantidade não pode ser menor.");
            }
            antes.put("quantidadeProfissionais", v.getQuantidadeProfissionais());
            depois.put("quantidadeProfissionais", req.quantidadeProfissionais());
            v.setQuantidadeProfissionais(req.quantidadeProfissionais().shortValue());
            if (v.getStatus() == StatusVaga.PREENCHIDA && req.quantidadeProfissionais() > ocupadas) v.setStatus(StatusVaga.PUBLICADA);
        }
        if (req.formaPagamento() != null && req.formaPagamento() != v.getFormaPagamento()) {
            if (temConfirmada) throw new RegraNegocioException("Já existe pagamento feito nesta vaga; a forma de pagamento não pode mudar.");
            antes.put("formaPagamento", v.getFormaPagamento()); depois.put("formaPagamento", req.formaPagamento());
            v.setFormaPagamento(req.formaPagamento());
        }
        if (req.descricao() != null) { depois.put("descricao", "alterada"); v.setDescricao(req.descricao()); }
        if (req.ofereceAlimentacao() != null) { depois.put("ofereceAlimentacao", req.ofereceAlimentacao()); v.setOfereceAlimentacao(req.ofereceAlimentacao()); }
        if (req.tipoAlimentacao() != null) { depois.put("tipoAlimentacao", req.tipoAlimentacao()); v.setTipoAlimentacao(req.tipoAlimentacao()); }
        if (req.disponivelAte() != null) { depois.put("disponivelAte", req.disponivelAte()); v.setDisponivelAte(req.disponivelAte()); }

        BigDecimal novoTotal = valorTotal(v);
        for (Contratacao c : confirmadas) {
            c.setValorAcordado(novoTotal);
            if (prejudica && c.getStatus() == StatusContratacao.CONFIRMADA) {
                c.setStatus(StatusContratacao.AGUARDANDO_ACEITE);
                notificacoes.notificar(c.getFreelancer().getId(), "CONTRATACAO",
                        "A vaga de " + v.getFuncao().getNome() + " mudou. Confira e aceite de novo.", c.getId());
            } else if (!depois.isEmpty()) {
                notificacoes.notificar(c.getFreelancer().getId(), "VAGA_ALTERADA",
                        "A vaga de " + v.getFuncao().getNome() + " foi atualizada.", v.getId());
            }
        }
        if (!depois.isEmpty()) auditoria.registrar("vaga", v.getId(), "ALTERACAO", antes, depois);
        return resposta(v);
    }

    @Transactional(readOnly = true)
    public PaginaResposta<HistoricoItem> historico(Long id, int pagina, int tamanho) {
        daEmpresaLogada(id);
        var page = logs.findByEntidadeAndEntidadeId("vaga", id, Paginas.de(pagina, tamanho, Sort.by(Sort.Direction.DESC, "registradoEm")));
        return PaginaResposta.de(page, l -> new HistoricoItem(l.getRegistradoEm(), l.getUsuarioId(), l.getAcao(),
                l.getDadosAnteriores() == null ? null : jsonMapper.readTree(l.getDadosAnteriores()),
                l.getDadosNovos() == null ? null : jsonMapper.readTree(l.getDadosNovos())));
    }

    /** Cancelar antes do início (KAN-69 RN13 a RN16): contratações canceladas e pagamentos estornados. */
    @Transactional
    public Resposta cancelar(Long id, CancelamentoRequest req) {
        Vaga v = vagas.buscarComLock(id).orElseThrow(() -> new RecursoNaoEncontradoException("Vaga", id));
        exigirDona(v);
        if (FINAIS.contains(v.getStatus())) throw new ConflitoException("A vaga já está " + v.getStatus() + ".");
        if (!v.getInicio().isAfter(LocalDateTime.now())) {
            throw new RegraNegocioException("O turno já começou. Use o cancelamento ou a substituição de cada contratação.");
        }
        Usuario empresa = v.getEmpresa();
        for (Contratacao c : contratacoes.findByCandidaturaVagaIdAndStatusIn(id,
                List.of(StatusContratacao.AGUARDANDO_ACEITE, StatusContratacao.CONFIRMADA))) {
            c.setStatus(StatusContratacao.CANCELADA);
            c.setCanceladaPor(empresa);
            c.setEncerradaEm(LocalDateTime.now());
            c.setMotivoEncerramento("Vaga cancelada: " + req.motivo());
            c.getCandidatura().setStatus(StatusCandidatura.RECUSADA);
            pagamentoService.estornarSeHouver(c, "Vaga cancelada pela empresa");
            notificacoes.notificar(c.getFreelancer().getId(), "CONTRATACAO",
                    "A vaga de " + v.getFuncao().getNome() + " foi cancelada pela empresa.", c.getId());
        }
        candidaturas.recusarPendentesDaVaga(id);
        StatusVaga anterior = v.getStatus();
        v.setStatus(StatusVaga.CANCELADA);
        auditoria.registrar("vaga", id, "MUDANCA_STATUS", Map.of("status", anterior),
                Map.of("status", "CANCELADA", "motivo", req.motivo()));
        return resposta(v);
    }

    @Transactional
    public void excluir(Long id) {
        Vaga v = daEmpresaLogada(id);
        if (v.getStatus() != StatusVaga.RASCUNHO) {
            throw new ConflitoException("Só vaga em RASCUNHO pode ser excluída. Para as outras, use o cancelamento.");
        }
        vagas.delete(v);
        auditoria.registrar("vaga", id, "EXCLUSAO", null, null);
    }

    // ------------------------------------------------------------------ freelancer: busca e favoritos

    /**
     * Busca de vagas abertas (Endpoints Freelancer, seção 2). Vagas com destaque ativo vêm primeiro.
     * Sem latitude/longitude no banco, o filtro de região é por cidade, UF ou início do CEP.
     * Premium vê as vagas 15 minutos antes dos demais.
     */
    @Transactional(readOnly = true)
    public PaginaResposta<Resposta> buscarAbertas(Integer funcaoId, String cidade, String uf, String cepPrefixo,
                                                  BigDecimal valorMin, BigDecimal valorMax,
                                                  LocalDateTime inicioDe, LocalDateTime inicioAte,
                                                  OrdenacaoBusca ordenarPor, Sort.Direction ordem,
                                                  int pagina, int tamanho) {
        LocalDateTime agora = LocalDateTime.now();
        boolean premium = UsuarioAtual.perfil() == TipoUsuario.FREELANCER && freelancerService.isPremium(UsuarioAtual.id());
        LocalDateTime publicadaAte = premium ? agora : agora.minusMinutes(props.regras().acessoAntecipadoPremiumMinutos());
        OrdenacaoBusca campo = ordenarPor == null ? OrdenacaoBusca.DATA_PUBLICACAO : ordenarPor;
        Sort.Direction direcao = ordem == null ? (campo == OrdenacaoBusca.DATA_PUBLICACAO ? Sort.Direction.DESC : Sort.Direction.ASC) : ordem;

        Specification<Vaga> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("status"), StatusVaga.PUBLICADA));
            p.add(cb.greaterThan(root.get("inicio"), agora));
            p.add(cb.lessThanOrEqualTo(root.get("publicadaEm"), publicadaAte));
            p.add(cb.or(cb.isNull(root.get("disponivelAte")), cb.greaterThan(root.get("disponivelAte"), agora)));
            Join<Vaga, Endereco> end = root.join("endereco");
            if (funcaoId != null) p.add(cb.equal(root.get("funcao").get("id"), funcaoId));
            if (cidade != null && !cidade.isBlank()) p.add(cb.equal(cb.lower(end.get("cidade")), cidade.trim().toLowerCase()));
            if (uf != null && !uf.isBlank()) p.add(cb.equal(end.get("uf"), uf.trim().toUpperCase()));
            if (cepPrefixo != null && !cepPrefixo.isBlank()) p.add(cb.like(end.get("cep"), cepPrefixo.replaceAll("\\D", "") + "%"));
            if (valorMin != null) p.add(cb.greaterThanOrEqualTo(root.get("valor"), valorMin));
            if (valorMax != null) p.add(cb.lessThanOrEqualTo(root.get("valor"), valorMax));
            if (inicioDe != null) p.add(cb.greaterThanOrEqualTo(root.get("inicio"), inicioDe));
            if (inicioAte != null) p.add(cb.lessThanOrEqualTo(root.get("inicio"), inicioAte));

            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                // Destaque ativo primeiro (UC15, RF19)
                Subquery<Long> destaque = query.subquery(Long.class);
                Root<Impulsionamento> imp = destaque.from(Impulsionamento.class);
                destaque.select(imp.get("id")).where(cb.equal(imp.get("vaga"), root),
                        cb.lessThanOrEqualTo(imp.get("inicio"), agora), cb.greaterThan(imp.get("fim"), agora));
                Expression<Integer> prioridade = cb.<Integer>selectCase().when(cb.exists(destaque), 0).otherwise(1);
                Path<?> ordenar = switch (campo) {
                    case VALOR -> root.get("valor");
                    case INICIO -> root.get("inicio");
                    case DATA_PUBLICACAO -> root.get("publicadaEm");
                };
                query.orderBy(cb.asc(prioridade),
                        direcao == Sort.Direction.ASC ? cb.asc(ordenar) : cb.desc(ordenar),
                        cb.asc(root.get("id")));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PaginaResposta.de(vagas.findAll(spec, Paginas.de(pagina, tamanho)), this::resposta);
    }

    @Transactional
    public void favoritar(Long vagaId) {
        Freelancer f = freelancerService.logado();
        Vaga v = carregar(vagaId);
        if (v.getStatus() == StatusVaga.RASCUNHO) throw new RecursoNaoEncontradoException("Vaga", vagaId);
        VagaFavoritaId chave = new VagaFavoritaId(f.getId(), vagaId);
        if (favoritas.existsById(chave)) return; // PUT é idempotente
        VagaFavorita fav = new VagaFavorita();
        fav.setId(chave);
        fav.setFreelancer(f);
        fav.setVaga(v);
        favoritas.save(fav);
    }

    @Transactional
    public void desfavoritar(Long vagaId) {
        favoritas.deleteById(new VagaFavoritaId(UsuarioAtual.id(), vagaId));
    }

    @Transactional(readOnly = true)
    public PaginaResposta<Resposta> listarFavoritas(int pagina, int tamanho) {
        var page = favoritas.listarDoFreelancer(UsuarioAtual.id(), Paginas.de(pagina, tamanho, Sort.by(Sort.Direction.DESC, "criadaEm")));
        return PaginaResposta.de(page, fav -> resposta(fav.getVaga()));
    }

    // ------------------------------------------------------------------ auxiliares

    public Vaga carregar(Long id) {
        return vagas.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Vaga", id));
    }

    public Vaga daEmpresaLogada(Long id) {
        Vaga v = carregar(id);
        exigirDona(v);
        return v;
    }

    public void exigirDona(Vaga v) {
        if (!v.getEmpresa().getId().equals(UsuarioAtual.id())) {
            throw new AcessoNegadoException("Esta vaga é de outra empresa.");
        }
    }

    /** Valor por profissional: valor/hora x horas do turno, ou o valor do turno/diária. */
    public static BigDecimal valorTotal(Vaga v) {
        if (v.getTipoValor() == TipoValor.HORA) {
            BigDecimal horas = BigDecimal.valueOf(Duration.between(v.getInicio(), v.getFim()).toMinutes())
                    .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
            return v.getValor().multiply(horas).setScale(2, RoundingMode.HALF_UP);
        }
        return v.getValor().setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal valorMinimo(TipoValor tipo) {
        return switch (tipo) {
            case HORA -> props.regras().valorMinimoHora();
            case TURNO -> props.regras().valorMinimoTurno();
            case DIARIA -> props.regras().valorMinimoDiaria();
        };
    }

    private Funcao funcao(Integer id) {
        return funcoes.findById(id).orElseThrow(() -> new RegraNegocioException("A função " + id + " não existe. Consulte GET /funcoes."));
    }

    public Resposta resposta(Vaga v) {
        long preenchidas = contratacoes.countByCandidaturaVagaIdAndStatusIn(v.getId(), CONFIRMADAS);
        boolean destacada = impulsionamentos.existsByVagaIdAndFimAfter(v.getId(), LocalDateTime.now());
        return Resposta.de(v, preenchidas, valorTotal(v), destacada);
    }
}
