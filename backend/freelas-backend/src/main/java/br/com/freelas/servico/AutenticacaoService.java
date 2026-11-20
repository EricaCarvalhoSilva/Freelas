package br.com.freelas.servico;

import br.com.freelas.config.PropriedadesFreelas;
import br.com.freelas.dto.AutenticacaoDto.*;
import br.com.freelas.entidade.TokenRenovacao;
import br.com.freelas.entidade.TokenVerificacao;
import br.com.freelas.entidade.Usuario;
import br.com.freelas.enums.FinalidadeToken;
import br.com.freelas.enums.StatusUsuario;
import br.com.freelas.excecao.AcessoNegadoException;
import br.com.freelas.excecao.ConflitoException;
import br.com.freelas.excecao.NaoAutenticadoException;
import br.com.freelas.excecao.RegraNegocioException;
import br.com.freelas.integracao.EmailService;
import br.com.freelas.repositorio.TokenRenovacaoRepository;
import br.com.freelas.repositorio.TokenVerificacaoRepository;
import br.com.freelas.repositorio.UsuarioRepository;
import br.com.freelas.seguranca.Hashes;
import br.com.freelas.seguranca.TokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Login, renovação, logout, confirmação de e-mail e recuperação de senha
 * (UC01, UC02, UC04; RNF01, RNF02, RNF05, RNF06).
 */
@Service
public class AutenticacaoService {

    private final UsuarioRepository usuarios;
    private final TokenRenovacaoRepository tokensRenovacao;
    private final TokenVerificacaoRepository tokensVerificacao;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditoriaService auditoria;
    private final PropriedadesFreelas props;

    public AutenticacaoService(UsuarioRepository usuarios, TokenRenovacaoRepository tokensRenovacao,
                               TokenVerificacaoRepository tokensVerificacao, TokenService tokenService,
                               PasswordEncoder passwordEncoder, EmailService emailService,
                               AuditoriaService auditoria, PropriedadesFreelas props) {
        this.usuarios = usuarios;
        this.tokensRenovacao = tokensRenovacao;
        this.tokensVerificacao = tokensVerificacao;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.auditoria = auditoria;
        this.props = props;
    }

    // ------------------------------------------------------------------ login e tokens

    @Transactional
    public TokenResposta login(LoginRequest req) {
        Usuario usuario = usuarios.findByEmailIgnoreCase(req.email().trim()).orElse(null);
        if (usuario == null || !passwordEncoder.matches(req.senha(), usuario.getSenhaHash())) {
            auditoria.registrarIsolado(usuario == null ? null : usuario.getId(), "usuario",
                    usuario == null ? 0L : usuario.getId(), "LOGIN_RECUSADO", Map.of("email", req.email()));
            throw new NaoAutenticadoException("E-mail ou senha incorretos.");
        }
        if (usuario.getStatus() == StatusUsuario.BLOQUEADO || usuario.getStatus() == StatusUsuario.EXCLUIDO) {
            auditoria.registrarIsolado(usuario.getId(), "usuario", usuario.getId(), "LOGIN_BLOQUEADO", null);
            throw new AcessoNegadoException("Conta " + usuario.getStatus().name().toLowerCase() + ". Procure o suporte.");
        }
        auditoria.registrarIsolado(usuario.getId(), "usuario", usuario.getId(), "LOGIN", null);
        return emitirTokens(usuario, null);
    }

    @Transactional
    public TokenResposta renovar(RenovacaoRequest req) {
        TokenRenovacao atual = tokensRenovacao.findByTokenHash(Hashes.sha256(req.tokenRenovacao()))
                .orElseThrow(() -> new NaoAutenticadoException("Token de renovação inválido."));
        LocalDateTime agora = LocalDateTime.now();
        if (atual.getRevogadoEm() != null || atual.getExpiraEm().isBefore(agora) || atual.getExpiraMaxEm().isBefore(agora)) {
            throw new NaoAutenticadoException("Sessão expirada. Faça login novamente.");
        }
        Usuario usuario = usuarios.findById(atual.getUsuarioId())
                .orElseThrow(() -> new NaoAutenticadoException("Token de renovação inválido."));
        if (usuario.getStatus() == StatusUsuario.BLOQUEADO || usuario.getStatus() == StatusUsuario.EXCLUIDO) {
            throw new NaoAutenticadoException("Conta sem acesso. Faça login novamente.");
        }
        atual.setRevogadoEm(agora); // rotação: o token usado não vale mais
        return emitirTokens(usuario, atual.getExpiraMaxEm());
    }

    @Transactional
    public void logout(RenovacaoRequest req) {
        tokensRenovacao.findByTokenHash(Hashes.sha256(req.tokenRenovacao())).ifPresent(t -> {
            if (t.getRevogadoEm() == null) t.setRevogadoEm(LocalDateTime.now());
            auditoria.registrar("usuario", t.getUsuarioId(), "LOGOUT", null, null);
        });
    }

    private TokenResposta emitirTokens(Usuario usuario, LocalDateTime limiteMaximo) {
        var seg = props.seguranca();
        LocalDateTime agora = LocalDateTime.now();
        String bruto = Hashes.tokenAleatorio();
        TokenRenovacao novo = new TokenRenovacao();
        novo.setUsuarioId(usuario.getId());
        novo.setTokenHash(Hashes.sha256(bruto));
        novo.setExpiraMaxEm(limiteMaximo != null ? limiteMaximo : agora.plusDays(seg.tokenRenovacaoMaximoDias()));
        LocalDateTime porInatividade = agora.plusMinutes(seg.tokenRenovacaoInatividadeMinutos());
        novo.setExpiraEm(porInatividade.isAfter(novo.getExpiraMaxEm()) ? novo.getExpiraMaxEm() : porInatividade);
        tokensRenovacao.save(novo);

        var acesso = tokenService.gerarTokenAcesso(usuario);
        return new TokenResposta(acesso.valor(), "Bearer", acesso.expiraEm(), bruto, usuario.getId(), usuario.getTipo());
    }

    // ------------------------------------------------------------------ códigos por e-mail

    /** Gera um código de 6 dígitos (válido por 15 min), guarda só o hash e envia por e-mail. */
    @Transactional
    public void enviarCodigo(Usuario usuario, FinalidadeToken finalidade, String destino, String valorNovo) {
        tokensVerificacao.invalidarAnteriores(usuario.getId(), finalidade, LocalDateTime.now());
        String codigo = Hashes.codigoNumerico();
        TokenVerificacao t = new TokenVerificacao();
        t.setUsuarioId(usuario.getId());
        t.setFinalidade(finalidade);
        t.setCodigoHash(Hashes.sha256(usuario.getId() + ":" + codigo));
        t.setValorNovo(valorNovo);
        t.setExpiraEm(LocalDateTime.now().plusMinutes(props.seguranca().codigoEmailMinutos()));
        tokensVerificacao.save(t);
        String assunto = switch (finalidade) {
            case CONFIRMACAO_EMAIL -> "Confirme seu e-mail no Freelas";
            case TROCA_EMAIL -> "Confirme o novo e-mail da sua conta Freelas";
            case RECUPERACAO_SENHA -> "Recuperação de senha do Freelas";
        };
        emailService.enviar(destino, assunto, "Seu código é " + codigo + " (vale por "
                + props.seguranca().codigoEmailMinutos() + " minutos).");
    }

    /** Confirma o e-mail do cadastro ou a troca de e-mail (UC01, KAN-63 RN03, RN05 e RN07). */
    @Transactional
    public MensagemResposta confirmarEmail(ConfirmacaoEmailRequest req) {
        Usuario usuario = usuarios.findByEmailIgnoreCase(req.email().trim())
                .orElseThrow(() -> new RegraNegocioException("Código inválido ou vencido."));
        if (validarCodigoSeExistir(usuario, FinalidadeToken.TROCA_EMAIL, req.codigo()) instanceof TokenVerificacao troca) {
            if (usuarios.existsByEmailIgnoreCase(troca.getValorNovo())) {
                throw new ConflitoException("Já existe uma conta com este e-mail.");
            }
            String anterior = usuario.getEmail();
            usuario.setEmail(troca.getValorNovo());
            auditoria.registrar("usuario", usuario.getId(), "ALTERACAO_EMAIL",
                    Map.of("email", anterior), Map.of("email", troca.getValorNovo()));
            return new MensagemResposta("E-mail alterado. Use o novo e-mail no próximo login.");
        }
        validarCodigo(usuario, FinalidadeToken.CONFIRMACAO_EMAIL, req.codigo());
        usuario.setEmailConfirmado(true);
        if (usuario.getStatus() == StatusUsuario.PENDENTE) {
            usuario.setStatus(StatusUsuario.ATIVO);
            auditoria.registrar("usuario", usuario.getId(), "MUDANCA_STATUS",
                    Map.of("status", "PENDENTE"), Map.of("status", "ATIVO"));
        }
        return new MensagemResposta("E-mail confirmado. Conta ativa.");
    }

    @Transactional
    public MensagemResposta reenviarConfirmacao(EmailRequest req) {
        usuarios.findByEmailIgnoreCase(req.email().trim())
                .filter(u -> !u.getEmailConfirmado())
                .ifPresent(u -> enviarCodigo(u, FinalidadeToken.CONFIRMACAO_EMAIL, u.getEmail(), null));
        return new MensagemResposta("Se o e-mail estiver cadastrado e pendente, um novo código foi enviado.");
    }

    /** "Esqueci a senha": resposta genérica para não revelar se o e-mail existe (Padrão da API, 3.3). */
    @Transactional
    public MensagemResposta solicitarRecuperacao(EmailRequest req) {
        usuarios.findByEmailIgnoreCase(req.email().trim())
                .filter(Usuario::getEmailConfirmado)
                .filter(u -> u.getStatus() != StatusUsuario.EXCLUIDO)
                .ifPresent(u -> enviarCodigo(u, FinalidadeToken.RECUPERACAO_SENHA, u.getEmail(), null));
        return new MensagemResposta("Se o e-mail estiver cadastrado, enviamos um código de recuperação.");
    }

    @Transactional
    public MensagemResposta redefinirSenha(RedefinicaoSenhaRequest req) {
        Usuario usuario = usuarios.findByEmailIgnoreCase(req.email().trim())
                .orElseThrow(() -> new RegraNegocioException("Código inválido ou vencido."));
        validarCodigo(usuario, FinalidadeToken.RECUPERACAO_SENHA, req.codigo());
        trocarSenha(usuario, req.novaSenha());
        return new MensagemResposta("Senha redefinida. Faça login com a nova senha.");
    }

    /** Troca de senha logado (UC04). */
    @Transactional
    public void alterarSenha(Usuario usuario, AlterarSenhaRequest req) {
        if (!passwordEncoder.matches(req.senhaAtual(), usuario.getSenhaHash())) {
            throw new RegraNegocioException("A senha atual está incorreta.");
        }
        if (passwordEncoder.matches(req.novaSenha(), usuario.getSenhaHash())) {
            throw new RegraNegocioException("A nova senha precisa ser diferente da atual.");
        }
        trocarSenha(usuario, req.novaSenha());
    }

    private void trocarSenha(Usuario usuario, String novaSenha) {
        usuario.setSenhaHash(passwordEncoder.encode(novaSenha));
        tokensRenovacao.revogarTodos(usuario.getId(), LocalDateTime.now()); // derruba as outras sessões
        auditoria.registrar("usuario", usuario.getId(), "ALTERACAO_SENHA", null, null);
    }

    private void validarCodigo(Usuario usuario, FinalidadeToken finalidade, String codigo) {
        if (validarCodigoSeExistir(usuario, finalidade, codigo) == null) {
            throw new RegraNegocioException("Código inválido ou vencido.");
        }
    }

    private TokenVerificacao validarCodigoSeExistir(Usuario usuario, FinalidadeToken finalidade, String codigo) {
        return tokensVerificacao.findFirstByUsuarioIdAndFinalidadeAndUsadoEmIsNullOrderByCriadoEmDesc(usuario.getId(), finalidade)
                .filter(t -> t.getExpiraEm().isAfter(LocalDateTime.now()))
                .filter(t -> Hashes.iguais(t.getCodigoHash(), Hashes.sha256(usuario.getId() + ":" + codigo)))
                .map(t -> { t.setUsadoEm(LocalDateTime.now()); return t; })
                .orElse(null);
    }
}
