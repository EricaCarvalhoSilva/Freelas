package br.com.freelas.excecao;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * 422: o dado é válido, mas uma regra de negócio impede a operação.
 * Pode levar a lista de motivos (ex.: publicação de vaga recusada por 3 motivos).
 */
public class RegraNegocioException extends ApiException {
    public RegraNegocioException(String mensagem) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "REGRA_NEGOCIO", mensagem, null);
    }

    public RegraNegocioException(String mensagem, List<String> motivos) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "REGRA_NEGOCIO", mensagem, motivos);
    }
}
