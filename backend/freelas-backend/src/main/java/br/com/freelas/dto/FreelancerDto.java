package br.com.freelas.dto;

import br.com.freelas.entidade.Formacao;
import br.com.freelas.entidade.Freelancer;
import br.com.freelas.entidade.FreelancerFuncao;
import br.com.freelas.enums.NivelReputacao;
import br.com.freelas.enums.StatusUsuario;
import br.com.freelas.enums.TipoFormacao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.URL;
import org.hibernate.validator.constraints.br.CPF;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** DTOs do perfil Freelancer (Endpoints Freelancer V1.1, seção 1). */
public final class FreelancerDto {
    private FreelancerDto() {}

    public record CadastroRequest(
            @NotBlank @Size(max = 120) String nome,
            @NotBlank @CPF(message = "CPF inválido") String cpf,
            @NotNull @Past(message = "deve ser uma data no passado") LocalDate dataNascimento,
            @NotBlank @Email @Size(max = 120) String email,
            @NotBlank @Pattern(regexp = Validacoes.SENHA, message = Validacoes.SENHA_MSG) String senha,
            @NotBlank @Pattern(regexp = Validacoes.TELEFONE, message = Validacoes.TELEFONE_MSG) String telefone,
            @NotNull @Valid EnderecoDto endereco,
            @NotNull @AssertTrue(message = "É preciso aceitar os termos de uso") Boolean aceiteTermos) {}

    /** PATCH: dados livres. Nome, CPF e nascimento só mudam por solicitação com documentos. */
    public record AtualizacaoRequest(
            @Pattern(regexp = Validacoes.TELEFONE, message = Validacoes.TELEFONE_MSG) String telefone,
            @Size(max = 2000) String apresentacao,
            @Valid EnderecoDto endereco,
            String nome,
            String cpf,
            LocalDate dataNascimento) {}

    public record FotoRequest(@NotBlank @URL(message = "URL inválida") @Size(max = 255) String fotoUrl) {}

    public record CurriculoRequest(@NotBlank @URL(message = "URL inválida") @Size(max = 255) String curriculoUrl) {}

    public record FuncaoItem(@NotNull Integer funcaoId, @Min(0) @Max(60) Integer anosExperiencia) {}

    public record FuncoesRequest(@NotNull @Size(min = 1, max = 10) List<@Valid @NotNull FuncaoItem> funcoes) {}

    public record FuncaoResposta(Integer funcaoId, String nome, Integer anosExperiencia) {
        public static FuncaoResposta de(FreelancerFuncao ff) {
            return new FuncaoResposta(ff.getFuncao().getId(), ff.getFuncao().getNome(),
                    ff.getAnosExperiencia() == null ? null : ff.getAnosExperiencia().intValue());
        }
    }

    public record FormacaoRequest(
            @NotBlank @Size(max = 120) String titulo,
            @Size(max = 120) String instituicao,
            TipoFormacao tipo,
            @Min(1950) @Max(2100) Integer anoConclusao) {}

    public record FormacaoResposta(Long id, String titulo, String instituicao, TipoFormacao tipo, Integer anoConclusao) {
        public static FormacaoResposta de(Formacao f) {
            return new FormacaoResposta(f.getId(), f.getTitulo(), f.getInstituicao(), f.getTipo(),
                    f.getAnoConclusao() == null ? null : f.getAnoConclusao().intValue());
        }
    }

    /** O próprio freelancer vê todos os dados. */
    public record Resposta(Long id, String nome, String cpf, LocalDate dataNascimento, String email, String telefone,
                           String fotoUrl, String apresentacao, String curriculoUrl, EnderecoDto endereco,
                           BigDecimal score, NivelReputacao nivelReputacao, Boolean verificado,
                           LocalDateTime suspensoAte, StatusUsuario status, Boolean emailConfirmado, Boolean premium,
                           List<FuncaoResposta> funcoes, List<FormacaoResposta> formacoes, LocalDateTime criadoEm) {
        public static Resposta de(Freelancer f, boolean premium, List<FuncaoResposta> funcoes, List<FormacaoResposta> formacoes) {
            return new Resposta(f.getId(), f.getNome(), f.getCpf(), f.getDataNascimento(), f.getEmail(), f.getTelefone(),
                    f.getFotoUrl(), f.getApresentacao(), f.getCurriculoUrl(), EnderecoDto.de(f.getEndereco()),
                    f.getScore(), f.getNivelReputacao(), f.getVerificado(), f.getSuspensoAte(), f.getStatus(),
                    f.getEmailConfirmado(), premium, funcoes, formacoes, f.getCriadoEm());
        }
    }

    /**
     * O que a empresa vê do candidato (UC08, KAN-70 seção 2): nunca CPF;
     * telefone e e-mail só depois de uma contratação confirmada com ela.
     */
    public record PerfilParaEmpresa(Long id, String nome, String fotoUrl, String apresentacao, String curriculoUrl,
                                    String cidade, String uf, BigDecimal score, NivelReputacao nivelReputacao,
                                    Boolean verificado, long trabalhosConcluidos, boolean ocorrenciaRecente,
                                    List<FuncaoResposta> funcoes, List<FormacaoResposta> formacoes, Contato contato) {}

    public record Contato(String telefone, String email) {}

    public record CompletudeResposta(int percentual, boolean completo, List<String> pendencias) {}
}
