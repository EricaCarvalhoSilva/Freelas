package br.com.freelas.dto;

import br.com.freelas.entidade.Funcao;
import br.com.freelas.entidade.Vaga;
import br.com.freelas.enums.FormaPagamento;
import br.com.freelas.enums.StatusVaga;
import br.com.freelas.enums.TipoValor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** DTOs de vagas (UC05, UC06, UC11; KAN-69). */
public final class VagaDto {
    private VagaDto() {}

    public record FuncaoResposta(Integer id, String nome) {
        public static FuncaoResposta de(Funcao f) { return new FuncaoResposta(f.getId(), f.getNome()); }
    }

    /** POST /vagas: nasce como RASCUNHO. Sem endereço, usa o endereço da empresa. */
    public record CriarRequest(
            @NotNull Integer funcaoId,
            @Valid EnderecoDto endereco,
            @Size(max = 2000) String descricao,
            @NotNull @Min(1) @Max(100) Integer quantidadeProfissionais,
            @NotNull @Future(message = "deve ser uma data futura") LocalDateTime inicio,
            @NotNull LocalDateTime fim,
            @NotNull @DecimalMin(value = "0.01", message = "deve ser maior que zero") @Digits(integer = 8, fraction = 2) BigDecimal valor,
            @NotNull TipoValor tipoValor,
            @NotNull Boolean ofereceAlimentacao,
            @Size(max = 40) String tipoAlimentacao,
            @NotNull FormaPagamento formaPagamento,
            LocalDateTime disponivelAte) {

        @AssertTrue(message = "O fim precisa ser depois do início")
        public boolean isPeriodoValido() {
            return inicio == null || fim == null || fim.isAfter(inicio);
        }

        @AssertTrue(message = "disponivelAte precisa ser antes do início do turno")
        public boolean isDisponibilidadeValida() {
            return disponivelAte == null || inicio == null || !disponivelAte.isAfter(inicio);
        }
    }

    /** PATCH /vagas/{id}: só os campos enviados mudam. Também serve para a forma de pagamento (UC11). */
    public record AtualizarRequest(
            Integer funcaoId,
            @Valid EnderecoDto endereco,
            @Size(max = 2000) String descricao,
            @Min(1) @Max(100) Integer quantidadeProfissionais,
            @Future(message = "deve ser uma data futura") LocalDateTime inicio,
            LocalDateTime fim,
            @DecimalMin(value = "0.01", message = "deve ser maior que zero") @Digits(integer = 8, fraction = 2) BigDecimal valor,
            TipoValor tipoValor,
            Boolean ofereceAlimentacao,
            @Size(max = 40) String tipoAlimentacao,
            FormaPagamento formaPagamento,
            LocalDateTime disponivelAte) {

        @AssertTrue(message = "O fim precisa ser depois do início")
        public boolean isPeriodoValido() {
            return inicio == null || fim == null || fim.isAfter(inicio);
        }
    }

    public record CancelamentoRequest(@NotBlank @Size(max = 255) String motivo) {}

    public record EmpresaResumo(Long id, String nomeFantasia, BigDecimal score) {}

    public record Resposta(Long id, EmpresaResumo empresa, FuncaoResposta funcao, EnderecoDto endereco,
                           String descricao, Integer quantidadeProfissionais, Long posicoesPreenchidas,
                           LocalDateTime inicio, LocalDateTime fim, BigDecimal valor, TipoValor tipoValor,
                           BigDecimal valorTotalPorProfissional, Boolean ofereceAlimentacao, String tipoAlimentacao,
                           FormaPagamento formaPagamento, Boolean relampago, StatusVaga status,
                           LocalDateTime publicadaEm, LocalDateTime disponivelAte, Boolean destacada) {
        public static Resposta de(Vaga v, long preenchidas, BigDecimal valorTotal, boolean destacada) {
            return new Resposta(v.getId(),
                    new EmpresaResumo(v.getEmpresa().getId(), v.getEmpresa().getNomeFantasia(), v.getEmpresa().getScore()),
                    FuncaoResposta.de(v.getFuncao()), EnderecoDto.de(v.getEndereco()), v.getDescricao(),
                    v.getQuantidadeProfissionais().intValue(), preenchidas, v.getInicio(), v.getFim(), v.getValor(),
                    v.getTipoValor(), valorTotal, v.getOfereceAlimentacao(), v.getTipoAlimentacao(),
                    v.getFormaPagamento(), v.getRelampago(), v.getStatus(), v.getPublicadaEm(), v.getDisponivelAte(),
                    destacada);
        }
    }

    public record HistoricoItem(LocalDateTime registradoEm, Long usuarioId, String acao,
                                JsonNode dadosAnteriores, JsonNode dadosNovos) {}

    /** Ordenações aceitas na busca de vagas do freelancer. */
    public enum OrdenacaoBusca { DATA_PUBLICACAO, VALOR, INICIO }
}
