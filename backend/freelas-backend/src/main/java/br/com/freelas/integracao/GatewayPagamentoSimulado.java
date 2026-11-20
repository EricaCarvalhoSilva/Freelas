package br.com.freelas.integracao;

import br.com.freelas.seguranca.Hashes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Gateway simulado para desenvolvimento e testes: aprova tudo, exceto token que contenha "recusado".
 * O id da transação é derivado da chave de idempotência, então repetir a chamada devolve o mesmo id.
 * Para produção, crie outra implementação de {@link GatewayPagamento} com o SDK do gateway escolhido.
 */
@Component
public class GatewayPagamentoSimulado implements GatewayPagamento {

    private static final Logger log = LoggerFactory.getLogger(GatewayPagamentoSimulado.class);

    @Override
    public Resultado cobrar(String token, BigDecimal valor, String chave, String descricao) {
        if (token != null && token.toLowerCase().contains("recusado")) {
            log.info("[gateway simulado] cobrança RECUSADA de R$ {} ({})", valor, descricao);
            return new Resultado(false, null, "Pagamento recusado pela operadora.");
        }
        String id = "sim_" + Hashes.sha256("cobranca:" + chave).substring(0, 24);
        log.info("[gateway simulado] cobrança aprovada {} de R$ {} ({})", id, valor, descricao);
        return new Resultado(true, id, "Aprovado");
    }

    @Override
    public Resultado cobrarPixAvulso(BigDecimal valor, String chave, String descricao) {
        String id = "sim_pix_" + Hashes.sha256("pix:" + chave).substring(0, 20);
        log.info("[gateway simulado] Pix avulso aprovado {} de R$ {} ({})", id, valor, descricao);
        return new Resultado(true, id, "Aprovado");
    }

    @Override
    public Resultado estornar(String idTransacao, BigDecimal valor) {
        log.info("[gateway simulado] estorno de R$ {} da transação {}", valor, idTransacao);
        return new Resultado(true, "est_" + idTransacao, "Estornado");
    }

    @Override
    public Resultado enviarPix(String chavePix, BigDecimal valor, String chave) {
        String id = "sim_payout_" + Hashes.sha256("payout:" + chave).substring(0, 20);
        log.info("[gateway simulado] payout Pix {} de R$ {}", id, valor);
        return new Resultado(true, id, "Enviado");
    }
}
