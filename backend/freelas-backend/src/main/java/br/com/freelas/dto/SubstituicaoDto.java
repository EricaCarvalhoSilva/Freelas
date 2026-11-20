package br.com.freelas.dto;

import br.com.freelas.entidade.Contratacao;
import br.com.freelas.entidade.Substituicao;
import br.com.freelas.entidade.Vaga;
import br.com.freelas.enums.MotivoSubstituicao;
import br.com.freelas.enums.StatusSubstituicao;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** DTOs da substituição automática (UC13, RF16, RF17). */
public final class SubstituicaoDto {
    private SubstituicaoDto() {}

    public record Request(
            @NotNull MotivoSubstituicao motivo,
            @NotNull @DecimalMin("0.5") @DecimalMax("200.0") @Digits(integer = 4, fraction = 1) BigDecimal raioKm) {}

    public record RaioRequest(@NotNull @DecimalMin("0.5") @DecimalMax("200.0") @Digits(integer = 4, fraction = 1) BigDecimal raioKm) {}

    public record Resposta(Long id, Long contratacaoOriginalId, Long vagaId, String funcao, String empresa,
                           LocalDateTime inicio, LocalDateTime fim, BigDecimal valorAcordado, String cidade,
                           MotivoSubstituicao motivo, BigDecimal raioKm, StatusSubstituicao status,
                           LocalDateTime solicitadaEm, Long freelancersAvisados, Long novaContratacaoId,
                           ContratacaoDto.Parte substituto) {
        public static Resposta de(Substituicao s, Long avisados) {
            Contratacao original = s.getContratacaoOriginal();
            Vaga v = original.getVaga();
            Contratacao nova = s.getNovaContratacao();
            return new Resposta(s.getId(), original.getId(), v.getId(), v.getFuncao().getNome(),
                    v.getEmpresa().getNomeFantasia(), v.getInicio(), v.getFim(), original.getValorAcordado(),
                    v.getEndereco().getCidade(), s.getMotivo(), s.getRaioKm(), s.getStatus(), s.getSolicitadaEm(),
                    avisados, nova == null ? null : nova.getId(),
                    nova == null ? null : new ContratacaoDto.Parte(nova.getFreelancer().getId(), nova.getFreelancer().getNome()));
        }
    }
}
