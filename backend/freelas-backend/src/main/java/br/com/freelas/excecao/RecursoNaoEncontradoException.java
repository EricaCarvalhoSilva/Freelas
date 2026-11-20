package br.com.freelas.excecao;

import org.springframework.http.HttpStatus;

/** 404: o recurso pedido não existe. */
public class RecursoNaoEncontradoException extends ApiException {
    public RecursoNaoEncontradoException(String recurso, Object id) {
        super(HttpStatus.NOT_FOUND, "NAO_ENCONTRADO", recurso + " " + id + " não encontrado(a).", null);
    }

    public RecursoNaoEncontradoException(String mensagem) {
        super(HttpStatus.NOT_FOUND, "NAO_ENCONTRADO", mensagem, null);
    }
}
