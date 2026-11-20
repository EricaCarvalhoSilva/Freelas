package br.com.freelas.dto;

import br.com.freelas.entidade.Contratacao;
import br.com.freelas.entidade.Ocorrencia;
import br.com.freelas.entidade.Vaga;
import br.com.freelas.enums.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** DTOs de contratação, aceite e execução do turno (UC09, KAN-70 RN05 a RN17). */
public final class ContratacaoDto {
    private ContratacaoDto() {}

    public record ContratarRequest(@NotNull Long candidaturaId) {}

    public record CancelamentoRequest(@NotBlank @Size(max = 255) String motivo) {}

    /** A geolocalização é conferida no app; o banco atual não guarda coordenadas. */
    public record CheckinRequest(
            @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude) {}

    public record OcorrenciaRequest(@NotNull TipoOcorrencia tipo) {}

    public record OcorrenciaResposta(Long id, Long contratacaoId, Long usuarioId, TipoOcorrencia tipo,
                                     BigDecimal impactoScore, LocalDateTime registradaEm) {
        public static OcorrenciaResposta de(Ocorrencia o) {
            return new OcorrenciaResposta(o.getId(), o.getContratacao().getId(), o.getUsuario().getId(), o.getTipo(),
                    o.getImpactoScore(), o.getRegistradaEm());
        }
    }

    /** Termos combinados (KAN-70 RN10). */
    public record Termos(Long vagaId, String funcao, LocalDateTime inicio, LocalDateTime fim, BigDecimal valor,
                         TipoValor tipoValor, Boolean ofereceAlimentacao, String tipoAlimentacao,
                         FormaPagamento formaPagamento, EnderecoDto local) {
        public static Termos de(Vaga v) {
            return new Termos(v.getId(), v.getFuncao().getNome(), v.getInicio(), v.getFim(), v.getValor(),
                    v.getTipoValor(), v.getOfereceAlimentacao(), v.getTipoAlimentacao(), v.getFormaPagamento(),
                    EnderecoDto.de(v.getEndereco()));
        }
    }

    public record Parte(Long id, String nome) {}

    public record Resposta(Long id, StatusContratacao status, BigDecimal valorAcordado, Termos termos,
                           Parte empresa, Parte freelancer, Long candidaturaId, LocalDateTime confirmadaEm,
                           LocalDateTime checkinEm, LocalDateTime checkoutEm, LocalDateTime encerradaEm,
                           String motivoEncerramento, StatusPagamento statusPagamento) {
        public static Resposta de(Contratacao c, StatusPagamento statusPagamento) {
            return new Resposta(c.getId(), c.getStatus(), c.getValorAcordado(), Termos.de(c.getVaga()),
                    new Parte(c.getEmpresa().getId(), c.getEmpresa().getNomeFantasia()),
                    new Parte(c.getFreelancer().getId(), c.getFreelancer().getNome()),
                    c.getCandidatura().getId(), c.getConfirmadaEm(), c.getCheckinEm(), c.getCheckoutEm(),
                    c.getEncerradaEm(), c.getMotivoEncerramento(), statusPagamento);
        }
    }
}
