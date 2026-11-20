package br.com.freelas.dto;

import br.com.freelas.entidade.Mensagem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** DTOs do chat entre empresa e candidato (UC10, RF14). */
public final class ChatDto {
    private ChatDto() {}

    public record MensagemRequest(
            @NotBlank @Size(max = 2000) String conteudo,
            @Size(max = 64) String idCliente) {}

    public record MensagemResposta(Long id, Long conversaId, Long remetenteId, String conteudo,
                                   LocalDateTime enviadaEm, LocalDateTime lidaEm, String idCliente) {
        public static MensagemResposta de(Mensagem m) {
            return new MensagemResposta(m.getId(), m.getConversa().getId(), m.getRemetente().getId(), m.getConteudo(),
                    m.getEnviadaEm(), m.getLidaEm(), m.getIdCliente());
        }
    }

    public record ConversaResposta(Long id, Long candidaturaId, Long vagaId, String funcao,
                                   ContratacaoDto.Parte empresa, ContratacaoDto.Parte freelancer,
                                   MensagemResposta ultimaMensagem, long naoLidas, LocalDateTime criadaEm) {}

    public record LeituraResposta(int marcadas) {}
}
