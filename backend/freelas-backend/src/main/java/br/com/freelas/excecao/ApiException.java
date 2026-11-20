package br.com.freelas.excecao;

import org.springframework.http.HttpStatus;

import java.util.List;

/** Base das exceções de negócio. Cada subclasse define o código HTTP da resposta. */
public abstract class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String codigo;
    private final List<String> motivos;

    protected ApiException(HttpStatus status, String codigo, String mensagem, List<String> motivos) {
        super(mensagem);
        this.status = status;
        this.codigo = codigo;
        this.motivos = motivos == null ? List.of() : List.copyOf(motivos);
    }

    public HttpStatus getStatus() { return status; }
    public String getCodigo() { return codigo; }
    public List<String> getMotivos() { return motivos; }
}
