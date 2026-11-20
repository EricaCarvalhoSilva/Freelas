package br.com.freelas.excecao;

import org.springframework.http.HttpStatus;

/** 401: credenciais erradas ou token inválido/vencido. */
public class NaoAutenticadoException extends ApiException {
    public NaoAutenticadoException(String mensagem) {
        super(HttpStatus.UNAUTHORIZED, "NAO_AUTENTICADO", mensagem, null);
    }
}
