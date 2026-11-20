package br.com.freelas.seguranca;

import br.com.freelas.excecao.ErroResposta;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/** Respostas 401 e 403 geradas pelos filtros do Spring Security, no mesmo formato JSON da API. */
@Component
public class RespostasSeguranca implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    public RespostasSeguranca(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest req, HttpServletResponse res, AuthenticationException ex) throws IOException {
        escrever(res, ErroResposta.de(401, "NAO_AUTENTICADO",
                "Token ausente, inválido ou vencido. Faça login novamente.", req.getRequestURI()));
    }

    @Override
    public void handle(HttpServletRequest req, HttpServletResponse res, AccessDeniedException ex) throws IOException {
        escrever(res, ErroResposta.de(403, "ACESSO_NEGADO",
                "Seu perfil não tem permissão para esta operação.", req.getRequestURI()));
    }

    private void escrever(HttpServletResponse res, ErroResposta erro) throws IOException {
        res.setStatus(erro.status());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(res.getOutputStream(), erro);
    }
}
