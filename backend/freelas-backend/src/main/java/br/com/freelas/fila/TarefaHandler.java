package br.com.freelas.fila;

import br.com.freelas.enums.TipoTarefa;
import tools.jackson.databind.JsonNode;

/** Cada tipo de tarefa da fila tem um handler. Se lançar exceção, a tarefa volta para a fila com espera. */
public interface TarefaHandler {
    TipoTarefa tipo();

    void executar(JsonNode payload);
}
