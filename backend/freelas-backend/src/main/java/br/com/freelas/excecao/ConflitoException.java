package br.com.freelas.excecao;

import org.springframework.http.HttpStatus;

/** 409: conflito com o estado atual (e-mail já cadastrado, vaga já preenchida, avaliação repetida...). */
public class ConflitoException extends ApiException {
    public ConflitoException(String mensagem) {
        super(HttpStatus.CONFLICT, "CONFLITO", mensagem, null);
    }
}
