package br.com.freelas.seguranca;

import br.com.freelas.config.PropriedadesFreelas;
import br.com.freelas.entidade.Usuario;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Emite o token de acesso (JWT assinado com HS256).
 * Leva só o id do usuário (subject) e o perfil; nada de CPF, CNPJ ou telefone (Padrão da API, 3.1).
 */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final PropriedadesFreelas props;

    public TokenService(JwtEncoder encoder, PropriedadesFreelas props) {
        this.encoder = encoder;
        this.props = props;
    }

    public TokenEmitido gerarTokenAcesso(Usuario usuario) {
        Instant agora = Instant.now();
        Instant expira = agora.plus(props.seguranca().tokenAcessoMinutos(), ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("freelas")
                .subject(usuario.getId().toString())
                .claim("perfil", usuario.getTipo().name())
                .issuedAt(agora)
                .expiresAt(expira)
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenEmitido(token, expira);
    }

    public record TokenEmitido(String valor, Instant expiraEm) {}
}
