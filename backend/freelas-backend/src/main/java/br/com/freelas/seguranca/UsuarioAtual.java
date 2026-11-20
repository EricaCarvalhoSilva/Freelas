package br.com.freelas.seguranca;

import br.com.freelas.enums.TipoUsuario;
import br.com.freelas.excecao.NaoAutenticadoException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Lê o usuário logado a partir do token validado.
 * Nunca confiar em usuarioId/empresaId enviado no corpo (Padrão da API, 3.6).
 */
public final class UsuarioAtual {

    private UsuarioAtual() {}

    public static Long id() {
        return jwt().map(j -> Long.valueOf(j.getSubject()))
                .orElseThrow(() -> new NaoAutenticadoException("Faça login para continuar."));
    }

    public static TipoUsuario perfil() {
        return jwt().map(j -> TipoUsuario.valueOf(j.getClaimAsString("perfil")))
                .orElseThrow(() -> new NaoAutenticadoException("Faça login para continuar."));
    }

    /** Id do usuário logado ou null quando a chamada não tem token (ex.: login). */
    public static Long idOuNulo() {
        return jwt().map(j -> Long.valueOf(j.getSubject())).orElse(null);
    }

    private static java.util.Optional<Jwt> jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return java.util.Optional.of(jwt);
        }
        return java.util.Optional.empty();
    }
}
