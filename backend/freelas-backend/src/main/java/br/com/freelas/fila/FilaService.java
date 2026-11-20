package br.com.freelas.fila;

import br.com.freelas.entidade.FilaTarefa;
import br.com.freelas.enums.TipoTarefa;
import br.com.freelas.repositorio.FilaTarefaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Coloca tarefas na fila (tabela fila_tarefa). Como a tarefa é gravada na mesma transação
 * da operação de negócio, ela só existe se a operação for confirmada (padrão outbox).
 */
@Service
public class FilaService {

    private final FilaTarefaRepository repositorio;
    private final JsonMapper jsonMapper;

    public FilaService(FilaTarefaRepository repositorio, JsonMapper jsonMapper) {
        this.repositorio = repositorio;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public FilaTarefa enfileirar(TipoTarefa tipo, Map<String, ?> payload) {
        return enfileirar(tipo, payload, LocalDateTime.now());
    }

    @Transactional
    public FilaTarefa enfileirar(TipoTarefa tipo, Map<String, ?> payload, LocalDateTime executarEm) {
        FilaTarefa t = new FilaTarefa();
        t.setTipo(tipo);
        t.setPayload(jsonMapper.writeValueAsString(payload));
        t.setExecutarEm(executarEm);
        return repositorio.save(t);
    }
}
