package br.com.freelas.dto;

import br.com.freelas.enums.TipoUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

/** Requisições e respostas de /autenticacao (compartilhado por Empresa, Freelancer e Administrador). */
public final class AutenticacaoDto {
    private AutenticacaoDto() {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String senha) {}

    public record TokenResposta(String tokenAcesso, String tipoToken, Instant expiraEm,
                                String tokenRenovacao, Long usuarioId, TipoUsuario perfil) {}

    public record RenovacaoRequest(@NotBlank String tokenRenovacao) {}

    public record ConfirmacaoEmailRequest(
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = Validacoes.CODIGO, message = Validacoes.CODIGO_MSG) String codigo) {}

    public record EmailRequest(@NotBlank @Email String email) {}

    public record RedefinicaoSenhaRequest(
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = Validacoes.CODIGO, message = Validacoes.CODIGO_MSG) String codigo,
            @NotBlank @Pattern(regexp = Validacoes.SENHA, message = Validacoes.SENHA_MSG) String novaSenha) {}

    public record AlterarSenhaRequest(
            @NotBlank String senhaAtual,
            @NotBlank @Pattern(regexp = Validacoes.SENHA, message = Validacoes.SENHA_MSG) String novaSenha) {}

    public record MensagemResposta(String mensagem) {}
}
