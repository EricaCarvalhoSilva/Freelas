package br.com.freelas.controlador;

import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/** Atalhos para respostas 201 (com cabeçalho Location) e 200/201 de operações idempotentes. */
final class Respostas {
    private Respostas() {}

    static <T> ResponseEntity<T> criado(String caminho, Object id, T corpo) {
        URI uri = ServletUriComponentsBuilder.fromCurrentContextPath().path(caminho).buildAndExpand(id).toUri();
        return ResponseEntity.created(uri).body(corpo);
    }

    /** 201 quando criou agora; 200 quando a mesma requisição foi repetida (Idempotency-Key ou conversa existente). */
    static <T> ResponseEntity<T> criadoOuExistente(boolean criado, T corpo) {
        return criado ? ResponseEntity.status(201).body(corpo) : ResponseEntity.ok(corpo);
    }
}
