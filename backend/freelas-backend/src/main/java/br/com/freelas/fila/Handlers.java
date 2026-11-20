package br.com.freelas.fila;

import br.com.freelas.entidade.Vaga;
import br.com.freelas.enums.StatusVaga;
import br.com.freelas.enums.TipoTarefa;
import br.com.freelas.repositorio.NotificacaoRepository;
import br.com.freelas.repositorio.VagaRepository;
import br.com.freelas.servico.CarteiraService;
import br.com.freelas.servico.ContratacaoService;
import br.com.freelas.servico.PagamentoService;
import br.com.freelas.servico.SubstituicaoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Handlers das tarefas da fila. Cada um roda dentro de uma transação aberta pelo {@link WorkerFila}. */
public final class Handlers {
    private Handlers() {}

    /** Aviso de vaga nova a todos os freelancers da função, com um único INSERT ... SELECT. */
    @Component
    public static class NotificarNovaVaga implements TarefaHandler {
        private static final Logger log = LoggerFactory.getLogger(NotificarNovaVaga.class);
        private final VagaRepository vagas;
        private final NotificacaoRepository notificacoes;

        public NotificarNovaVaga(VagaRepository vagas, NotificacaoRepository notificacoes) {
            this.vagas = vagas;
            this.notificacoes = notificacoes;
        }

        public TipoTarefa tipo() { return TipoTarefa.NOTIFICAR_NOVA_VAGA; }

        public void executar(JsonNode payload) {
            Vaga v = vagas.findById(payload.get("vagaId").asLong()).orElse(null);
            if (v == null || v.getStatus() != StatusVaga.PUBLICADA) return;
            String mensagem = "Nova vaga de " + v.getFuncao().getNome() + " em " + v.getEmpresa().getNomeFantasia()
                    + " (" + v.getEndereco().getCidade() + ").";
            int total = notificacoes.notificarNovaVagaEmLote(v.getFuncao().getId(), v.getId(), mensagem);
            log.info("Vaga {} publicada: {} freelancers avisados.", v.getId(), total);
        }
    }

    @Component
    public static class BuscarSubstitutos implements TarefaHandler {
        private final SubstituicaoService servico;
        public BuscarSubstitutos(SubstituicaoService servico) { this.servico = servico; }
        public TipoTarefa tipo() { return TipoTarefa.BUSCAR_SUBSTITUTOS; }
        public void executar(JsonNode payload) { servico.buscarSubstitutos(payload.get("substituicaoId").asLong()); }
    }

    /** Fim das 6 h depois do check-out sem confirmação nem disputa: libera o pagamento. */
    @Component
    public static class LiberarPagamento implements TarefaHandler {
        private final ContratacaoService servico;
        public LiberarPagamento(ContratacaoService servico) { this.servico = servico; }
        public TipoTarefa tipo() { return TipoTarefa.LIBERAR_PAGAMENTO; }
        public void executar(JsonNode payload) { servico.liberarAutomaticamente(payload.get("pagamentoId").asLong()); }
    }

    @Component
    public static class ProcessarSaque implements TarefaHandler {
        private final CarteiraService servico;
        public ProcessarSaque(CarteiraService servico) { this.servico = servico; }
        public TipoTarefa tipo() { return TipoTarefa.PROCESSAR_SAQUE; }
        public void executar(JsonNode payload) { servico.processarSaque(payload.get("movimentacaoId").asLong()); }
    }

    @Component
    public static class ProcessarWebhook implements TarefaHandler {
        private final PagamentoService servico;
        public ProcessarWebhook(PagamentoService servico) { this.servico = servico; }
        public TipoTarefa tipo() { return TipoTarefa.PROCESSAR_WEBHOOK; }
        public void executar(JsonNode payload) { servico.processarWebhook(payload); }
    }
}
