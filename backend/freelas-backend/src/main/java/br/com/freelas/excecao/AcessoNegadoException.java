package br.com.freelas.excecao;

import org.springframework.http.HttpStatus;

/** 403: token válido, mas o recurso é de outra conta ou a conta não pode fazer a ação (RNF07). */
public class AcessoNegadoException extends ApiException {
    public AcessoNegadoException(String mensagem) {
        super(HttpStatus.FORBIDDEN, "ACESSO_NEGADO", mensagem, null);
    }
}
