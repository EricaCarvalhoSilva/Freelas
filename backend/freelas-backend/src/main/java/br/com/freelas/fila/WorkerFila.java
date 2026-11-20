package br.com.freelas.fila;

import br.com.freelas.config.PropriedadesFreelas;
import br.com.freelas.enums.TipoTarefa;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Worker da fila de tarefas no PostgreSQL.
 * Pega um lote com FOR UPDATE SKIP LOCKED: várias instâncias do backend podem rodar o worker
 * ao mesmo tempo sem pegar a mesma tarefa. Em caso de erro, tenta de novo com espera crescente.
 */
@Component
public class WorkerFila {

    private static final Logger log = LoggerFactory.getLogger(WorkerFila.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacao;
    private final JsonMapper jsonMapper;
    private final PropriedadesFreelas props;
    private final Map<TipoTarefa, TarefaHandler> handlers = new EnumMap<>(TipoTarefa.class);

    public WorkerFila(JdbcTemplate jdbc, TransactionTemplate transacao, JsonMapper jsonMapper,
                      PropriedadesFreelas props, List<TarefaHandler> lista) {
        this.jdbc = jdbc;
        this.transacao = transacao;
        this.jsonMapper = jsonMapper;
        this.props = props;
        lista.forEach(h -> handlers.put(h.tipo(), h));
    }

    @Scheduled(fixedDelayString = "${freelas.worker.intervalo-ms:2000}", initialDelay = 5000)
    public void processar() {
        List<Map<String, Object>> lote = jdbc.queryForList("""
                UPDATE fila_tarefa SET status = 'PROCESSANDO', tentativas = tentativas + 1, atualizada_em = now()
                WHERE id IN (SELECT id FROM fila_tarefa
                             WHERE status = 'PENDENTE' AND executar_em <= now()
                             ORDER BY executar_em
                             LIMIT ? FOR UPDATE SKIP LOCKED)
                RETURNING id, tipo, payload::text AS payload, tentativas, max_tentativas
                """, props.worker().lote());
        for (Map<String, Object> tarefa : lote) {
            executar(tarefa);
        }
    }

    private void executar(Map<String, Object> tarefa) {
        long id = ((Number) tarefa.get("id")).longValue();
        TipoTarefa tipo = TipoTarefa.valueOf((String) tarefa.get("tipo"));
        try {
            TarefaHandler handler = handlers.get(tipo);
            if (handler == null) throw new IllegalStateException("Sem handler para " + tipo);
            JsonNode payload = jsonMapper.readTree((String) tarefa.get("payload"));
            transacao.executeWithoutResult(s -> handler.executar(payload));
            jdbc.update("UPDATE fila_tarefa SET status = 'CONCLUIDA', atualizada_em = now() WHERE id = ?", id);
        } catch (Exception e) {
            int tentativas = ((Number) tarefa.get("tentativas")).intValue();
            int maximo = ((Number) tarefa.get("max_tentativas")).intValue();
            boolean desistir = tentativas >= maximo;
            long esperaSegundos = (long) Math.min(3600, 10 * Math.pow(2, tentativas)); // 20 s, 40 s, 80 s...
            String erro = String.valueOf(e.getMessage());
            log.warn("Tarefa {} ({}) falhou na tentativa {}: {}", id, tipo, tentativas, erro);
            jdbc.update("""
                    UPDATE fila_tarefa SET status = ?, ultimo_erro = ?, atualizada_em = now(),
                           executar_em = now() + make_interval(secs => ?)
                    WHERE id = ?
                    """, desistir ? "FALHOU" : "PENDENTE", erro.length() > 500 ? erro.substring(0, 500) : erro,
                    esperaSegundos, id);
        }
    }
}
