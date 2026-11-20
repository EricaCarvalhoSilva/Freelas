package br.com.freelas.dto;

import br.com.freelas.entidade.Candidatura;
import br.com.freelas.entidade.Freelancer;
import br.com.freelas.entidade.Vaga;
import br.com.freelas.enums.NivelReputacao;
import br.com.freelas.enums.StatusCandidatura;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** DTOs de candidatura (UC07, RF7, KAN-70 RN01 a RN04). */
public final class CandidaturaDto {
    private CandidaturaDto() {}

    public record VagaResumo(Long id, String funcao, String empresa, LocalDateTime inicio, LocalDateTime fim, String cidade) {
        public static VagaResumo de(Vaga v) {
            return new VagaResumo(v.getId(), v.getFuncao().getNome(), v.getEmpresa().getNomeFantasia(),
                    v.getInicio(), v.getFim(), v.getEndereco().getCidade());
        }
    }

    public record Resposta(Long id, VagaResumo vaga, Long freelancerId, String freelancerNome,
                           StatusCandidatura status, LocalDateTime criadaEm, Long contratacaoId) {
        public static Resposta de(Candidatura c, Long contratacaoId) {
            return new Resposta(c.getId(), VagaResumo.de(c.getVaga()), c.getFreelancer().getId(),
                    c.getFreelancer().getNome(), c.getStatus(), c.getCriadaEm(), contratacaoId);
        }
    }

    /** Linha da lista de candidatos que a empresa vê (sem CPF, telefone ou e-mail). */
    public record CandidatoResposta(Long candidaturaId, StatusCandidatura status, LocalDateTime criadaEm,
                                    Long freelancerId, String nome, String fotoUrl, BigDecimal score,
                                    NivelReputacao nivelReputacao, Boolean verificado, long trabalhosConcluidos,
                                    boolean ocorrenciaRecente, String cidade) {
        public static CandidatoResposta de(Candidatura c, long trabalhos, boolean ocorrenciaRecente) {
            Freelancer f = c.getFreelancer();
            return new CandidatoResposta(c.getId(), c.getStatus(), c.getCriadaEm(), f.getId(), f.getNome(),
                    f.getFotoUrl(), f.getScore(), f.getNivelReputacao(), f.getVerificado(), trabalhos, ocorrenciaRecente,
                    f.getEndereco() == null ? null : f.getEndereco().getCidade());
        }
    }

    /** PATCH /candidaturas/{id}: a empresa só pode recusar (status RECUSADA). */
    public record AtualizacaoRequest(@NotNull StatusCandidatura status) {}

    /** Ordenações da lista de candidatos (UC07). Distância depende da integração de mapas. */
    public enum Ordenacao { DATA_CANDIDATURA, SCORE, TRABALHOS_CONCLUIDOS }
}
