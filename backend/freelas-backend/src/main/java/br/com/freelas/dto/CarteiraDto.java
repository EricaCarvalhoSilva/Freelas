package br.com.freelas.dto;

import br.com.freelas.enums.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** DTOs da carteira, dados de recebimento e saques (Regras: Pagamentos, carteira e ganhos). */
public final class CarteiraDto {
    private CarteiraDto() {}

    public record SaldosResposta(BigDecimal saldoDisponivel, BigDecimal saldoPendente, BigDecimal saldoBloqueado,
                                 BigDecimal total) {}

    /** Linha do extrato: nos créditos de turno vêm a vaga, o estabelecimento, horários, bruto, taxa e líquido. */
    public record MovimentacaoResposta(Long id, TipoMovimentacao tipo, BigDecimal valor, StatusMovimentacao status,
                                       LocalDateTime criadaEm, Long pagamentoId, Long vagaId, String estabelecimento,
                                       LocalDateTime inicio, LocalDateTime fim, BigDecimal valorBruto,
                                       BigDecimal taxaPlataforma, BigDecimal valorLiquido, Long contaRecebimentoId) {}

    public record ContaRecebimentoRequest(
            @NotNull TipoContaRecebimento tipo,
            TipoChavePix tipoChavePix,
            @Size(max = 120) String chavePix,
            @Size(max = 60) String banco,
            @Pattern(regexp = "\\d{1,6}", message = "agência só com números") String agencia,
            @Pattern(regexp = "[0-9\\-]{1,20}", message = "conta só com números e hífen") String conta) {

        @AssertTrue(message = "Para PIX informe tipoChavePix e chavePix")
        public boolean isPixCompleto() {
            return tipo != TipoContaRecebimento.PIX || (tipoChavePix != null && chavePix != null && !chavePix.isBlank());
        }

        @AssertTrue(message = "Para CONTA_BANCARIA informe banco, agencia e conta")
        public boolean isContaCompleta() {
            return tipo != TipoContaRecebimento.CONTA_BANCARIA || (banco != null && agencia != null && conta != null);
        }
    }

    /** Dados mascarados: a chave completa nunca sai da API. */
    public record ContaRecebimentoResposta(Long id, TipoContaRecebimento tipo, TipoChavePix tipoChavePix,
                                           String chavePixMascarada, String banco, String agenciaMascarada,
                                           String contaMascarada, StatusContaRecebimento status,
                                           LocalDateTime liberadaSaqueEm, LocalDateTime criadaEm) {}

    public record SaqueRequest(
            @NotNull Long contaRecebimentoId,
            @NotNull @DecimalMin(value = "1.00", message = "o saque mínimo é R$ 1,00") @Digits(integer = 10, fraction = 2) BigDecimal valor) {}

    public record SaqueResposta(Long id, BigDecimal valor, StatusMovimentacao status, Long contaRecebimentoId,
                                LocalDateTime criadaEm) {}

    public enum Periodo { HOJE, SETE_DIAS, MES, TOTAL }

    public record GanhosResposta(Periodo periodo, BigDecimal totalLiberado, long turnosConcluidos,
                                 BigDecimal horasTrabalhadas, BigDecimal valorMedioPorHora) {}
}
