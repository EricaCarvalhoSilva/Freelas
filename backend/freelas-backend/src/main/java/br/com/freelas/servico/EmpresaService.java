package br.com.freelas.servico;

import br.com.freelas.dto.AutenticacaoDto.AlterarSenhaRequest;
import br.com.freelas.dto.EmpresaDto.*;
import br.com.freelas.dto.EnderecoDto;
import br.com.freelas.dto.Validacoes;
import br.com.freelas.entidade.Empresa;
import br.com.freelas.entidade.MeioPagamentoEmpresa;
import br.com.freelas.enums.FinalidadeToken;
import br.com.freelas.enums.StatusContratacao;
import br.com.freelas.enums.StatusUsuario;
import br.com.freelas.enums.TipoUsuario;
import br.com.freelas.excecao.ConflitoException;
import br.com.freelas.excecao.RecursoNaoEncontradoException;
import br.com.freelas.excecao.RegraNegocioException;
import br.com.freelas.repositorio.*;
import br.com.freelas.seguranca.UsuarioAtual;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Cadastro, perfil e meios de pagamento da empresa (UC01, UC03, UC04, UC11; KAN-63). */
@Service
public class EmpresaService {

    private final EmpresaRepository empresas;
    private final UsuarioRepository usuarios;
    private final EnderecoRepository enderecos;
    private final MeioPagamentoEmpresaRepository meiosPagamento;
    private final ContratacaoRepository contratacoes;
    private final TokenRenovacaoRepository tokensRenovacao;
    private final AutenticacaoService autenticacao;
    private final AuditoriaService auditoria;
    private final PasswordEncoder passwordEncoder;

    public EmpresaService(EmpresaRepository empresas, UsuarioRepository usuarios, EnderecoRepository enderecos,
                          MeioPagamentoEmpresaRepository meiosPagamento, ContratacaoRepository contratacoes,
                          TokenRenovacaoRepository tokensRenovacao, AutenticacaoService autenticacao,
                          AuditoriaService auditoria, PasswordEncoder passwordEncoder) {
        this.empresas = empresas;
        this.usuarios = usuarios;
        this.enderecos = enderecos;
        this.meiosPagamento = meiosPagamento;
        this.contratacoes = contratacoes;
        this.tokensRenovacao = tokensRenovacao;
        this.autenticacao = autenticacao;
        this.auditoria = auditoria;
        this.passwordEncoder = passwordEncoder;
    }

    /** UC01: a conta nasce PENDENTE e o código de confirmação vai por e-mail (KAN-63 RN01 a RN04). */
    @Transactional
    public Resposta cadastrar(CadastroRequest req) {
        String cnpj = Validacoes.soDigitos(req.cnpj());
        if (usuarios.existsByEmailIgnoreCase(req.email().trim())) {
            throw new ConflitoException("Já existe uma conta com este e-mail.");
        }
        if (empresas.existsByCnpj(cnpj)) {
            throw new ConflitoException("Já existe uma empresa com este CNPJ.");
        }
        Empresa e = new Empresa();
        e.setRazaoSocial(req.razaoSocial().trim());
        e.setNomeFantasia(req.nomeFantasia().trim());
        e.setCnpj(cnpj);
        e.setEmail(req.email().trim().toLowerCase());
        e.setSenhaHash(passwordEncoder.encode(req.senha()));
        e.setTelefone(req.telefone().trim());
        e.setCategoria(req.categoria());
        e.setResponsavelNome(req.responsavelNome());
        e.setEndereco(enderecos.save(req.endereco().paraEntidade()));
        empresas.save(e);
        autenticacao.enviarCodigo(e, FinalidadeToken.CONFIRMACAO_EMAIL, e.getEmail(), null);
        auditoria.registrar("empresa", e.getId(), "CADASTRO", null, Map.of("email", e.getEmail(), "cnpj", cnpj));
        return Resposta.de(e);
    }

    /** UC03: a própria empresa vê tudo; os demais veem o perfil público. */
    @Transactional(readOnly = true)
    public Object buscar(Long id) {
        Empresa e = carregar(id);
        if (id.equals(UsuarioAtual.id()) || UsuarioAtual.perfil() == TipoUsuario.ADMINISTRADOR) {
            return Resposta.de(e);
        }
        return PublicaResposta.de(e);
    }

    @Transactional
    public Resposta atualizar(Long id, AtualizacaoRequest req) {
        Empresa e = daEmpresaLogada(id);
        Contas.exigirAtiva(e);
        if (req.cnpj() != null && !Validacoes.soDigitos(req.cnpj()).equals(e.getCnpj())) {
            throw new RegraNegocioException("A troca de CNPJ só é feita pelo suporte (KAN-63 RN09).");
        }
        Map<String, Object> antes = new HashMap<>();
        Map<String, Object> depois = new HashMap<>();
        if (req.razaoSocial() != null) mudar(antes, depois, "razaoSocial", e.getRazaoSocial(), req.razaoSocial().trim(), e::setRazaoSocial);
        if (req.nomeFantasia() != null) mudar(antes, depois, "nomeFantasia", e.getNomeFantasia(), req.nomeFantasia().trim(), e::setNomeFantasia);
        if (req.telefone() != null) mudar(antes, depois, "telefone", e.getTelefone(), req.telefone().trim(), e::setTelefone);
        if (req.categoria() != null) { antes.put("categoria", e.getCategoria()); depois.put("categoria", req.categoria()); e.setCategoria(req.categoria()); }
        if (req.responsavelNome() != null) mudar(antes, depois, "responsavelNome", e.getResponsavelNome(), req.responsavelNome(), e::setResponsavelNome);

        String aviso = null;
        if (req.email() != null && !req.email().equalsIgnoreCase(e.getEmail())) {
            String novo = req.email().trim().toLowerCase();
            if (usuarios.existsByEmailIgnoreCase(novo)) throw new ConflitoException("Já existe uma conta com este e-mail.");
            autenticacao.enviarCodigo(e, FinalidadeToken.TROCA_EMAIL, novo, novo);
            aviso = "Enviamos um código para o novo e-mail. A troca só vale depois da confirmação (KAN-63 RN07).";
        }
        if (!depois.isEmpty()) auditoria.registrar("empresa", e.getId(), "ALTERACAO", antes, depois);
        return Resposta.de(e, aviso);
    }

    /** Novo endereço vira um registro novo: as vagas já criadas continuam no local combinado. */
    @Transactional
    public Resposta alterarEndereco(Long id, EnderecoDto req) {
        Empresa e = daEmpresaLogada(id);
        Contas.exigirAtiva(e);
        EnderecoDto anterior = EnderecoDto.de(e.getEndereco());
        e.setEndereco(enderecos.save(req.paraEntidade()));
        auditoria.registrar("empresa", e.getId(), "ALTERACAO_ENDERECO", anterior, req);
        String aviso = contratacoes.existsByCandidaturaVagaEmpresaIdAndStatusIn(id,
                List.of(StatusContratacao.AGUARDANDO_ACEITE, StatusContratacao.CONFIRMADA, StatusContratacao.EM_ANDAMENTO))
                ? "Há contratações em andamento. As vagas já criadas continuam com o endereço antigo; avise os freelancers se o local mudou."
                : null;
        return Resposta.de(e, aviso);
    }

    @Transactional
    public void alterarSenha(Long id, AlterarSenhaRequest req) {
        Empresa e = daEmpresaLogada(id);
        Contas.exigirAtiva(e);
        autenticacao.alterarSenha(e, req);
    }

    /** RNF22 (LGPD): pede a exclusão da conta. Contratações em andamento impedem. */
    @Transactional
    public void excluir(Long id) {
        Empresa e = daEmpresaLogada(id);
        if (contratacoes.existsByCandidaturaVagaEmpresaIdAndStatusIn(id,
                List.of(StatusContratacao.CONFIRMADA, StatusContratacao.EM_ANDAMENTO))) {
            throw new RegraNegocioException("Há contratações confirmadas ou em andamento. Conclua ou cancele antes de excluir a conta.");
        }
        StatusUsuario anterior = e.getStatus();
        e.setStatus(StatusUsuario.EXCLUIDO);
        tokensRenovacao.revogarTodos(id, LocalDateTime.now());
        auditoria.registrar("empresa", id, "EXCLUSAO_CONTA", Map.of("status", anterior), Map.of("status", "EXCLUIDO"));
    }

    // ------------------------------------------------------------------ meios de pagamento (KAN-69 RN06)

    @Transactional(readOnly = true)
    public List<MeioPagamentoResposta> listarMeios(Long id) {
        daEmpresaLogada(id);
        return meiosPagamento.findByEmpresaIdOrderByIdDesc(id).stream().map(MeioPagamentoResposta::de).toList();
    }

    @Transactional
    public MeioPagamentoResposta cadastrarMeio(Long id, MeioPagamentoRequest req) {
        Empresa e = daEmpresaLogada(id);
        Contas.exigirAtiva(e);
        MeioPagamentoEmpresa m = new MeioPagamentoEmpresa();
        m.setEmpresa(e);
        m.setTipo(req.tipo());
        m.setTokenGateway(req.tokenGateway());
        m.setFinalCartao(req.finalCartao());
        meiosPagamento.save(m);
        auditoria.registrar("meio_pagamento_empresa", m.getId(), "CADASTRO", null,
                Map.of("tipo", m.getTipo(), "finalCartao", String.valueOf(m.getFinalCartao())));
        return MeioPagamentoResposta.de(m);
    }

    @Transactional
    public void desativarMeio(Long id, Long meioId) {
        daEmpresaLogada(id);
        MeioPagamentoEmpresa m = meiosPagamento.findByIdAndEmpresaId(meioId, id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Meio de pagamento", meioId));
        m.setAtivo(false);
        auditoria.registrar("meio_pagamento_empresa", meioId, "DESATIVACAO", null, null);
    }

    // ------------------------------------------------------------------ auxiliares

    public Empresa carregar(Long id) {
        return empresas.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Empresa", id));
    }

    /** Carrega a empresa do caminho e confere se é a do token (403 se não for). */
    public Empresa daEmpresaLogada(Long id) {
        Contas.exigirMesmoUsuario(id, UsuarioAtual.id());
        return carregar(id);
    }

    /** Empresa logada (quando o caminho não tem o id). */
    public Empresa logada() {
        return carregar(UsuarioAtual.id());
    }

    private static void mudar(Map<String, Object> antes, Map<String, Object> depois, String campo,
                              String valorAntigo, String valorNovo, java.util.function.Consumer<String> setter) {
        antes.put(campo, valorAntigo);
        depois.put(campo, valorNovo);
        setter.accept(valorNovo);
    }
}
