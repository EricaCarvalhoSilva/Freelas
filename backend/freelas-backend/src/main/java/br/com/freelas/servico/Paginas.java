package br.com.freelas.servico;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Converte ?pagina (começa em 1) e ?tamanho para o Pageable do Spring (começa em 0). */
public final class Paginas {
    private Paginas() {}

    public static Pageable de(int pagina, int tamanho, Sort ordem) {
        return PageRequest.of(pagina - 1, tamanho, ordem);
    }

    public static Pageable de(int pagina, int tamanho) {
        return PageRequest.of(pagina - 1, tamanho);
    }
}
