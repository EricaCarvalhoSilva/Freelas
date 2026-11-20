package br.com.freelas.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Resposta paginada: registros em "dados" e metadados em "paginacao" (Padrão da API, 2.4). */
public record PaginaResposta<T>(List<T> dados, Paginacao paginacao) {

    public record Paginacao(int pagina, int tamanho, long totalItens, int totalPaginas) {}

    public static <E, T> PaginaResposta<T> de(Page<E> page, Function<E, T> conversor) {
        return new PaginaResposta<>(page.getContent().stream().map(conversor).toList(),
                new Paginacao(page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages()));
    }

    public static <T> PaginaResposta<T> deLista(List<T> todos, int pagina, int tamanho) {
        int inicio = Math.min((pagina - 1) * tamanho, todos.size());
        int fim = Math.min(inicio + tamanho, todos.size());
        int totalPaginas = (int) Math.ceil(todos.size() / (double) tamanho);
        return new PaginaResposta<>(todos.subList(inicio, fim), new Paginacao(pagina, tamanho, todos.size(), totalPaginas));
    }
}
