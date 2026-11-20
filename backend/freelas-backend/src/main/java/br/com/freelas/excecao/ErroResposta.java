package br.com.freelas.excecao;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Formato único de erro da API.
 *
 * <pre>
 * {
 *   "timestamp": "2026-11-19T18:00:00",
 *   "status": 400,
 *   "erro": "VALIDACAO",
 *   "mensagem": "Um ou mais campos estão inválidos.",
 *   "caminho": "/vagas",
 *   "campos": [ { "campo": "valor", "mensagem": "deve ser maior que 0", "valorRejeitado": -10 } ]
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErroResposta(
        LocalDateTime timestamp,
        int status,
        String erro,
        String mensagem,
        String caminho,
        List<CampoInvalido> campos,
        List<String> motivos
) {
    /** Um campo que não passou na validação. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CampoInvalido(String campo, String mensagem, Object valorRejeitado) {}

    public static ErroResposta de(int status, String erro, String mensagem, String caminho) {
        return new ErroResposta(LocalDateTime.now(), status, erro, mensagem, caminho, List.of(), List.of());
    }
}
