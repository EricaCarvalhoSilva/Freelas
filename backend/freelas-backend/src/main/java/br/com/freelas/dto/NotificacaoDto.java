package br.com.freelas.dto;

import br.com.freelas.entidade.Notificacao;
import br.com.freelas.enums.Plataforma;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record NotificacaoDto(Long id, String tipo, String mensagem, Long referenciaId, LocalDateTime criadaEm, Boolean lida) {

    public static NotificacaoDto de(Notificacao n) {
        return new NotificacaoDto(n.getId(), n.getTipo(), n.getMensagem(), n.getReferenciaId(), n.getCriadaEm(), n.getLida());
    }

    public record LeituraRequest(@NotNull Boolean lida) {}

    public record DispositivoRequest(@NotBlank @Size(max = 255) String tokenPush, @NotNull Plataforma plataforma) {}

    public record DispositivoResposta(Long id, Plataforma plataforma, LocalDateTime registradoEm) {}
}
