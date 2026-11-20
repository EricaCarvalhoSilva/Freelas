package br.com.freelas.enums;

/** Tipos de tarefa processados pelo worker da fila (tabela fila_tarefa). */
public enum TipoTarefa {
    NOTIFICAR_NOVA_VAGA,
    BUSCAR_SUBSTITUTOS,
    LIBERAR_PAGAMENTO,
    PROCESSAR_SAQUE,
    PROCESSAR_WEBHOOK
}
