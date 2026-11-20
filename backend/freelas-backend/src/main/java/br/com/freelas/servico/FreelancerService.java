package br.com.freelas.servico;

import br.com.freelas.config.PropriedadesFreelas;
import br.com.freelas.dto.AutenticacaoDto.AlterarSenhaRequest;
import br.com.freelas.dto.FreelancerDto.*;
import br.com.freelas.dto.Validacoes;
import br.com.freelas.entidade.*;
import br.com.freelas.enums.*;
import br.com.freelas.excecao.AcessoNegadoException;
import br.com.freelas.excecao.ConflitoException;
import br.com.freelas.excecao.RecursoNaoEncontradoException;
import br.com.freelas.excecao.RegraNegocioException;
import br.com.freelas.repositorio.*;
import br.com.freelas.seguranca.UsuarioAtual;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Cadastro e perfil do freelancer (Endpoints Freelancer V1.1, seção 1; RF5, RF6, RF8). */
@Service
public class FreelancerService {

    private static final List<StatusContratacao> ATIVAS =
            List.of(StatusContratacao.CONFIRMADA, StatusContratacao.EM_ANDAMENTO);

    private final FreelancerRepository freelancers;
    private final UsuarioRepository usuarios;
    private final EnderecoRepository enderecos;
    private final CarteiraRepository carteiras;
    private final FuncaoRepository funcoes;
    private final FreelancerFuncaoRepository freelancerFuncoes;
    private final FormacaoRepository formacoes;
    private final CandidaturaRepository candidaturas;
    private final ContratacaoRepository contratacoes;
    private final OcorrenciaRepository ocorrencias;
    private final AssinaturaRepository assinaturas;
    private final TokenRenovacaoRepository tokensRenovacao;
    private final AutenticacaoService autenticacao;
    private final AuditoriaService auditoria;
    private final PasswordEncoder passwordEncoder;
    private final PropriedadesFreelas props;

    public FreelancerService(FreelancerRepository freelancers, UsuarioRepository usuarios, EnderecoRepository enderecos,
                             CarteiraRepository carteiras, FuncaoRepository funcoes,
                             FreelancerFuncaoRepository freelancerFuncoes, FormacaoRepository formacoes,
                             CandidaturaRepository candidaturas, ContratacaoRepository contratacoes,
                             OcorrenciaRepository ocorrencias, AssinaturaRepository assinaturas,
                             TokenRenovacaoRepository tokensRenovacao, AutenticacaoService autenticacao,
                             AuditoriaService auditoria, PasswordEncoder passwordEncoder, PropriedadesFreelas props) {
        this.freelancers = freelancers;
        this.usuarios = usuarios;
        this.enderecos = enderecos;
        this.carteiras = carteiras;
        this.funcoes = funcoes;
        this.freelancerFuncoes = freelancerFuncoes;
        this.formacoes = formacoes;
        this.candidaturas = candidaturas;
        this.contratacoes = contratacoes;
        this.ocorrencias = ocorrencias;
        this.assinaturas = assinaturas;
        this.tokensRenovacao = tokensRenovacao;
        this.autenticacao = autenticacao;
        this.auditoria = auditoria;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    /** Cadastro com idade mínima de 18 anos; cria a carteira junto (composição 1:1). */
    @Transactional
    public Resposta cadastrar(CadastroRequest req) {
        int idade = Period.between(req.dataNascimento(), LocalDate.now()).getYears();
        if (idade < props.regras().idadeMinima()) {
            throw new RegraNegocioException("É preciso ter pelo menos " + props.regras().idadeMinima() + " anos para se cadastrar.");
        }
        String cpf = Validacoes.soDigitos(req.cpf());
        if (usuarios.existsByEmailIgnoreCase(req.email().trim())) throw new ConflitoException("Já existe uma conta com este e-mail.");
        if (freelancers.existsByCpf(cpf)) throw new ConflitoException("Já existe um freelancer com este CPF.");

        Freelancer f = new Freelancer();
        f.setNome(req.nome().trim());
        f.setCpf(cpf);
        f.setDataNascimento(req.dataNascimento());
        f.setEmail(req.email().trim().toLowerCase());
        f.setSenhaHash(passwordEncoder.encode(req.senha()));
        f.setTelefone(req.telefone().trim());
        f.setEndereco(enderecos.save(req.endereco().paraEntidade()));
        freelancers.save(f);

        Carteira carteira = new Carteira();
        carteira.setFreelancer(f);
        carteiras.save(carteira);

        autenticacao.enviarCodigo(f, FinalidadeToken.CONFIRMACAO_EMAIL, f.getEmail(), null);
        auditoria.registrar("freelancer", f.getId(), "CADASTRO", null, Map.of("email", f.getEmail(), "aceiteTermos", true));
        return montarResposta(f);
    }

    @Transactional(readOnly = true)
    public Resposta meuPerfil() {
        return montarResposta(logado());
    }

    @Transactional
    public Resposta atualizar(AtualizacaoRequest req) {
        Freelancer f = logado();
        Contas.exigirAtiva(f);
        if (req.nome() != null || req.cpf() != null || req.dataNascimento() != null) {
            throw new RegraNegocioException("Nome, CPF e data de nascimento só mudam por solicitação com documentos (análise manual).");
        }
        if (req.telefone() != null) {
            auditoria.registrar("freelancer", f.getId(), "ALTERACAO_TELEFONE", Map.of("telefone", f.getTelefone()), Map.of("telefone", req.telefone()));
            f.setTelefone(req.telefone().trim());
        }
        if (req.apresentacao() != null) f.setApresentacao(req.apresentacao().trim());
        if (req.endereco() != null) {
            auditoria.registrar("freelancer", f.getId(), "ALTERACAO_ENDERECO", null, req.endereco());
            f.setEndereco(enderecos.save(req.endereco().paraEntidade()));
        }
        return montarResposta(f);
    }

    @Transactional
    public Resposta alterarFoto(FotoRequest req) {
        Freelancer f = logado();
        Contas.exigirAtiva(f);
        f.setFotoUrl(req.fotoUrl());
        return montarResposta(f);
    }

    @Transactional
    public Resposta alterarCurriculo(CurriculoRequest req) {
        Freelancer f = logado();
        Contas.exigirAtiva(f);
        f.setCurriculoUrl(req.curriculoUrl());
        return montarResposta(f);
    }

    /** Substitui as funções (categorias de atuação) do freelancer. */
    @Transactional
    public List<FuncaoResposta> definirFuncoes(FuncoesRequest req) {
        Freelancer f = logado();
        Contas.exigirAtiva(f);
        var ids = new HashSet<Integer>();
        for (FuncaoItem item : req.funcoes()) {
            if (!ids.add(item.funcaoId())) throw new RegraNegocioException("Função " + item.funcaoId() + " repetida na lista.");
        }
        freelancerFuncoes.apagarDoFreelancer(f.getId());
        freelancerFuncoes.flush();
        for (FuncaoItem item : req.funcoes()) {
            Funcao funcao = funcoes.findById(item.funcaoId())
                    .orElseThrow(() -> new RegraNegocioException("Função " + item.funcaoId() + " não existe."));
            FreelancerFuncao ff = new FreelancerFuncao();
            ff.setFreelancer(f);
            ff.setFuncao(funcao);
            ff.setAnosExperiencia(item.anosExperiencia() == null ? null : item.anosExperiencia().shortValue());
            freelancerFuncoes.save(ff);
        }
        return listarFuncoes(f.getId());
    }

    @Transactional
    public FormacaoResposta adicionarFormacao(FormacaoRequest req) {
        Freelancer f = logado();
        Contas.exigirAtiva(f);
        Formacao formacao = new Formacao();
        formacao.setFreelancer(f);
        formacao.setTitulo(req.titulo().trim());
        formacao.setInstituicao(req.instituicao());
        formacao.setTipo(req.tipo());
        formacao.setAnoConclusao(req.anoConclusao() == null ? null : req.anoConclusao().shortValue());
        return FormacaoResposta.de(formacoes.save(formacao));
    }

    @Transactional
    public void removerFormacao(Long formacaoId) {
        Formacao formacao = formacoes.findByIdAndFreelancerId(formacaoId, UsuarioAtual.id())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Formação", formacaoId));
        formacoes.delete(formacao);
    }

    @Transactional
    public void alterarSenha(AlterarSenhaRequest req) {
        autenticacao.alterarSenha(logado(), req);
    }

    /** O que falta no perfil (impacta candidatura, Boost e visibilidade). */
    @Transactional(readOnly = true)
    public CompletudeResposta completude() {
        return completude(logado());
    }

    public CompletudeResposta completude(Freelancer f) {
        List<String> pendencias = new ArrayList<>();
        if (!f.getEmailConfirmado()) pendencias.add("Confirmar o e-mail");
        if (f.getFotoUrl() == null) pendencias.add("Enviar a foto");
        if (f.getApresentacao() == null || f.getApresentacao().isBlank()) pendencias.add("Escrever a apresentação");
        if (f.getEndereco() == null) pendencias.add("Informar o endereço");
        if (freelancerFuncoes.listarDoFreelancer(f.getId()).isEmpty()) pendencias.add("Escolher pelo menos uma função");
        if (f.getCurriculoUrl() == null) pendencias.add("Enviar o currículo");
        int total = 6;
        int percentual = (total - pendencias.size()) * 100 / total;
        return new CompletudeResposta(percentual, pendencias.isEmpty(), pendencias);
    }

    /** RNF22 (LGPD). */
    @Transactional
    public void excluir() {
        Freelancer f = logado();
        if (contratacoes.existsByCandidaturaFreelancerIdAndStatusIn(f.getId(), ATIVAS)) {
            throw new RegraNegocioException("Há contratações confirmadas ou em andamento. Conclua ou cancele antes de excluir a conta.");
        }
        Carteira carteira = carteiras.findByFreelancerId(f.getId()).orElseThrow();
        if (carteira.getSaldoDisponivel().signum() > 0 || carteira.getSaldoPendente().signum() > 0
                || carteira.getSaldoBloqueado().signum() > 0) {
            throw new RegraNegocioException("Saque o saldo da carteira antes de excluir a conta.");
        }
        f.setStatus(StatusUsuario.EXCLUIDO);
        tokensRenovacao.revogarTodos(f.getId(), LocalDateTime.now());
        auditoria.registrar("freelancer", f.getId(), "EXCLUSAO_CONTA", null, Map.of("status", "EXCLUIDO"));
    }

    /** UC08: a empresa só vê quem se candidatou a uma vaga dela. */
    @Transactional(readOnly = true)
    public PerfilParaEmpresa perfilParaEmpresa(Long freelancerId) {
        Freelancer f = exigirPodeVer(freelancerId);
        Long logado = UsuarioAtual.id();
        // Telefone e e-mail só depois de uma contratação confirmada com esta empresa (KAN-70 seção 2)
        Contato contato = UsuarioAtual.perfil() == TipoUsuario.EMPRESA && temContratacaoConfirmadaCom(freelancerId, logado)
                ? new Contato(f.getTelefone(), f.getEmail()) : null;
        Endereco e = f.getEndereco();
        return new PerfilParaEmpresa(f.getId(), f.getNome(), f.getFotoUrl(), f.getApresentacao(), f.getCurriculoUrl(),
                e == null ? null : e.getCidade(), e == null ? null : e.getUf(), f.getScore(), f.getNivelReputacao(),
                f.getVerificado(), contratacoes.countByCandidaturaFreelancerIdAndStatus(freelancerId, StatusContratacao.CONCLUIDA),
                ocorrencias.existsByUsuarioIdAndRegistradaEmAfter(freelancerId, LocalDateTime.now().minusDays(30)),
                listarFuncoes(freelancerId), listarFormacoes(freelancerId), contato);
    }

    /** Admin, o próprio freelancer ou empresa que recebeu candidatura dele. */
    public Freelancer exigirPodeVer(Long freelancerId) {
        Freelancer f = carregar(freelancerId);
        Long logado = UsuarioAtual.id();
        TipoUsuario perfil = UsuarioAtual.perfil();
        boolean podeVer = perfil == TipoUsuario.ADMINISTRADOR || logado.equals(freelancerId)
                || (perfil == TipoUsuario.EMPRESA && candidaturas.existsByFreelancerIdAndVagaEmpresaId(freelancerId, logado));
        if (!podeVer) throw new AcessoNegadoException("A empresa só vê o perfil de quem se candidatou às vagas dela.");
        return f;
    }

    private boolean temContratacaoConfirmadaCom(Long freelancerId, Long empresaId) {
        return contratacoes.existsConfirmadaEntre(freelancerId, empresaId);
    }

    // ------------------------------------------------------------------ auxiliares

    public Freelancer carregar(Long id) {
        return freelancers.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Freelancer", id));
    }

    public Freelancer logado() {
        return carregar(UsuarioAtual.id());
    }

    public boolean isPremium(Long usuarioId) {
        return !assinaturas.vigentes(usuarioId, LocalDateTime.now()).isEmpty();
    }

    public List<FuncaoResposta> listarFuncoes(Long freelancerId) {
        return freelancerFuncoes.listarDoFreelancer(freelancerId).stream().map(FuncaoResposta::de).toList();
    }

    private List<FormacaoResposta> listarFormacoes(Long freelancerId) {
        return formacoes.findByFreelancerIdOrderByAnoConclusaoDesc(freelancerId).stream().map(FormacaoResposta::de).toList();
    }

    private Resposta montarResposta(Freelancer f) {
        return Resposta.de(f, isPremium(f.getId()), listarFuncoes(f.getId()), listarFormacoes(f.getId()));
    }
}
