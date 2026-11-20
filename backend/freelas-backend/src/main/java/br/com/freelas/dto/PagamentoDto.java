package br.com.freelas.dto;

import br.com.freelas.entidade.Pagamento;
import br.com.freelas.enums.StatusPagamento;
import br.com.freelas.enums.TipoMeioPagamento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** DTOs do pagamento em custódia (Fluxo de Integração do Pagamento; RNF16, RNF17, RNF24). */
public final class PagamentoDto {
    private PagamentoDto() {}

    public record Resposta(Long id, Long contratacaoId, BigDecimal valorBruto, BigDecimal taxaPlataforma,
                           BigDecimal valorLiquido, StatusPagamento status, String idTransacaoGateway,
                           TipoMeioPagamento meioPagamento, String finalCartao, LocalDateTime pagoEm,
                           LocalDateTime liberadoEm, LocalDateTime liberacaoAutomaticaEm, String motivoDisputa) {
        public static Resposta de(Pagamento p, LocalDateTime liberacaoAutomaticaEm) {
            return new Resposta(p.getId(), p.getContratacao().getId(), p.getValorBruto(), p.getTaxaPlataforma(),
                    p.getValorLiquido(), p.getStatus(), p.getIdTransacaoGateway(), p.getMeioPagamento().getTipo(),
                    p.getMeioPagamento().getFinalCartao(), p.getPagoEm(), p.getLiberadoEm(), liberacaoAutomaticaEm,
                    p.getMotivoDisputa());
        }
    }

    public record DisputaRequest(@NotBlank @Size(max = 500) String motivo) {}

    /** Corpo enviado pelo gateway (formato do gateway simulado). */
    public record WebhookRequest(@NotBlank String idTransacaoGateway, @NotBlank String evento, String status) {}

    public record DecisaoDisputaRequest(@NotNull Boolean favoravelAoFreelancer, @Size(max = 500) String observacao) {}
}
