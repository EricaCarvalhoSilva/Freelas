package br.com.freelas.integracao;

import java.math.BigDecimal;

/**
 * Porta para o gateway de pagamento (Asaas ou Mercado Pago, ainda em decisão no grupo).
 * O backend é o único que fala com o gateway; o app nunca chama a API financeira.
 */
public interface GatewayPagamento {

    /** Cobra o meio de pagamento tokenizado. A chave de idempotência evita cobrança dupla (RNF16). */
    Resultado cobrar(String tokenMeioPagamento, BigDecimal valor, String chaveIdempotencia, String descricao);

    /** Cobrança Pix avulsa (Premium e Boost do freelancer). */
    Resultado cobrarPixAvulso(BigDecimal valor, String chaveIdempotencia, String descricao);

    /** Devolve o valor de uma cobrança (estorno). */
    Resultado estornar(String idTransacao, BigDecimal valor);

    /** Payout Pix para a chave do freelancer (saque). */
    Resultado enviarPix(String chavePix, BigDecimal valor, String chaveIdempotencia);

    record Resultado(boolean aprovado, String idTransacao, String mensagem) {}
}
