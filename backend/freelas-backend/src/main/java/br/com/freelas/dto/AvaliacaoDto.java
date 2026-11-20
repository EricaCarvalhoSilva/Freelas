package br.com.freelas.dto;

import br.com.freelas.entidade.Avaliacao;
import br.com.freelas.entidade.Contestacao;
import br.com.freelas.enums.DirecaoAvaliacao;
import br.com.freelas.enums.NivelReputacao;
import br.com.freelas.enums.StatusAvaliacao;
import br.com.freelas.enums.StatusContestacao;
import br.com.freelas.enums.TipoOcorrencia;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** DTOs de avaliação, contestação e score (UC12, RF15, RF22, RF24; nota de 0 a 5). */
public final class AvaliacaoDto {
    private AvaliacaoDto() {}

    public record CriterioRequest(
            @NotBlank @Size(max = 30) String criterio,
            @NotNull @DecimalMin("0.0") @DecimalMax("5.0") @Digits(integer = 1, fraction = 1) BigDecimal nota) {}

    public record Request(
            @NotNull @DecimalMin(value = "0.0", message = "a nota vai de 0 a 5") @DecimalMax(value = "5.0", message = "a nota vai de 0 a 5")
            @Digits(integer = 1, fraction = 1) BigDecimal nota,
            @Size(max = 500) String comentario,
            @Size(max = 10) List<@Valid @NotNull CriterioRequest> criterios) {}

    public record CriterioResposta(String criterio, BigDecimal nota) {}

    /** Enquanto a avaliação está oculta (avaliação cega), nota e comentário não aparecem. */
    public record Resposta(Long id, Long contratacaoId, DirecaoAvaliacao direcao, Long avaliadorId, Long avaliadoId,
                           BigDecimal nota, String comentario, List<CriterioResposta> criterios,
                           LocalDateTime enviadaEm, Boolean visivel, StatusAvaliacao status) {
        public static Resposta de(Avaliacao a, boolean mostrarConteudo) {
            return new Resposta(a.getId(), a.getContratacao().getId(), a.getDirecao(), a.getAvaliador().getId(),
                    a.getAvaliado().getId(), mostrarConteudo ? a.getNota() : null, mostrarConteudo ? a.getComentario() : null,
                    mostrarConteudo ? a.getCriterios().stream().map(c -> new CriterioResposta(c.getCriterio(), c.getNota())).toList() : null,
                    a.getEnviadaEm(), a.getVisivel(), a.getStatus());
        }
    }

    public record ContestacaoRequest(
            @NotBlank @Size(min = 20, max = 2000, message = "explique em 20 a 2000 caracteres") String justificativa,
            @URL(message = "URL inválida") @Size(max = 255) String evidenciaUrl) {}

    public record ContestacaoResposta(Long id, Long avaliacaoId, Long solicitanteId, String justificativa,
                                      String evidenciaUrl, StatusContestacao status, LocalDateTime abertaEm,
                                      LocalDateTime resolvidaEm) {
        public static ContestacaoResposta de(Contestacao c) {
            return new ContestacaoResposta(c.getId(), c.getAvaliacao().getId(), c.getSolicitante().getId(),
                    c.getJustificativa(), c.getEvidenciaUrl(), c.getStatus(), c.getAbertaEm(), c.getResolvidaEm());
        }
    }

    public record DecisaoContestacaoRequest(@NotNull Boolean aceita) {}

    public record OcorrenciaResumo(TipoOcorrencia tipo, BigDecimal impacto, LocalDateTime registradaEm, Long contratacaoId) {}

    public record ScoreResposta(BigDecimal score, NivelReputacao nivelReputacao, BigDecimal mediaNotas90Dias,
                                BigDecimal penalidades90Dias, long trabalhosConcluidos, List<OcorrenciaResumo> ocorrencias90Dias,
                                String formula) {}

    public record PendenteResposta(Long contratacaoId, String empresa, String funcao, LocalDateTime encerradaEm,
                                   LocalDateTime prazoAte) {}
}
