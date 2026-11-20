package br.com.freelas.dto;

import br.com.freelas.entidade.Empresa;
import br.com.freelas.entidade.MeioPagamentoEmpresa;
import br.com.freelas.enums.CategoriaEmpresa;
import br.com.freelas.enums.StatusUsuario;
import br.com.freelas.enums.TipoMeioPagamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.br.CNPJ;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** DTOs do perfil Empresa (UC01, UC03, UC04, UC11; KAN-63). */
public final class EmpresaDto {
    private EmpresaDto() {}

    public record CadastroRequest(
            @NotBlank @Size(max = 150) String razaoSocial,
            @NotBlank @Size(max = 150) String nomeFantasia,
            @NotBlank @CNPJ(message = "CNPJ inválido") String cnpj,
            @NotBlank @Email @Size(max = 120) String email,
            @NotBlank @Pattern(regexp = Validacoes.SENHA, message = Validacoes.SENHA_MSG) String senha,
            @NotBlank @Pattern(regexp = Validacoes.TELEFONE, message = Validacoes.TELEFONE_MSG) String telefone,
            @NotNull CategoriaEmpresa categoria,
            @Size(max = 120) String responsavelNome,
            @NotNull @Valid EnderecoDto endereco) {}

    /** PATCH: só os campos enviados são alterados. */
    public record AtualizacaoRequest(
            @Size(min = 1, max = 150) String razaoSocial,
            @Size(min = 1, max = 150) String nomeFantasia,
            @Pattern(regexp = Validacoes.TELEFONE, message = Validacoes.TELEFONE_MSG) String telefone,
            CategoriaEmpresa categoria,
            @Size(max = 120) String responsavelNome,
            @Email @Size(max = 120) String email,
            String cnpj) {}

    public record Resposta(Long id, String razaoSocial, String nomeFantasia, String cnpj, String email,
                           String telefone, CategoriaEmpresa categoria, String responsavelNome,
                           EnderecoDto endereco, BigDecimal score, StatusUsuario status,
                           Boolean emailConfirmado, LocalDateTime criadoEm, String aviso) {
        public static Resposta de(Empresa e) {
            return de(e, null);
        }

        public static Resposta de(Empresa e, String aviso) {
            return new Resposta(e.getId(), e.getRazaoSocial(), e.getNomeFantasia(), e.getCnpj(), e.getEmail(),
                    e.getTelefone(), e.getCategoria(), e.getResponsavelNome(), EnderecoDto.de(e.getEndereco()),
                    e.getScore(), e.getStatus(), e.getEmailConfirmado(), e.getCriadoEm(), aviso);
        }
    }

    /** O que um freelancer vê da empresa (sem e-mail e telefone). */
    public record PublicaResposta(Long id, String nomeFantasia, CategoriaEmpresa categoria, String cidade,
                                  String uf, BigDecimal score) {
        public static PublicaResposta de(Empresa e) {
            return new PublicaResposta(e.getId(), e.getNomeFantasia(), e.getCategoria(),
                    e.getEndereco().getCidade(), e.getEndereco().getUf(), e.getScore());
        }
    }

    public record MeioPagamentoRequest(
            @NotNull TipoMeioPagamento tipo,
            @NotBlank @Size(max = 120) String tokenGateway,
            @Pattern(regexp = "\\d{4}", message = "Informe os 4 últimos dígitos do cartão") String finalCartao) {

        @AssertTrue(message = "Cartão precisa dos 4 últimos dígitos (finalCartao)")
        public boolean isFinalCartaoInformado() {
            return tipo != TipoMeioPagamento.CARTAO || finalCartao != null;
        }
    }

    public record MeioPagamentoResposta(Long id, TipoMeioPagamento tipo, String finalCartao, Boolean ativo) {
        public static MeioPagamentoResposta de(MeioPagamentoEmpresa m) {
            return new MeioPagamentoResposta(m.getId(), m.getTipo(), m.getFinalCartao(), m.getAtivo());
        }
    }
}
