package br.com.freelas.controlador;

import br.com.freelas.dto.ChatDto.*;
import br.com.freelas.servico.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/** Chat entre empresa e candidato (REST; o aviso de mensagem nova vai por notificação). */
@RestController
@RequestMapping("/conversas")
@Tag(name = "Chat")
public class ConversaController {

    private final ChatService chat;

    public ConversaController(ChatService chat) {
        this.chat = chat;
    }

    @GetMapping("/{id}/mensagens")
    @Operation(summary = "Mensagens da conversa, da mais nova para a mais antiga (use antesDe para paginar)")
    public List<MensagemResposta> mensagens(@PathVariable Long id,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime antesDe,
                                            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int tamanho) {
        return chat.mensagens(id, antesDe, tamanho);
    }

    @PostMapping("/{id}/mensagens")
    @Operation(summary = "Enviar mensagem (idCliente evita duplicar no reenvio)")
    public ResponseEntity<MensagemResposta> enviar(@PathVariable Long id, @Valid @RequestBody MensagemRequest req) {
        var r = chat.enviar(id, req);
        return Respostas.criadoOuExistente(r.criada(), r.mensagem());
    }

    @PostMapping("/{id}/leitura")
    @Operation(summary = "Marcar como lidas as mensagens recebidas")
    public LeituraResposta leitura(@PathVariable Long id) {
        return chat.marcarLidas(id);
    }
}
