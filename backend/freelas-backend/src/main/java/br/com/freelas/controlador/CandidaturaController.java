package br.com.freelas.controlador;

import br.com.freelas.dto.CandidaturaDto.*;
import br.com.freelas.dto.ChatDto.ConversaResposta;
import br.com.freelas.servico.CandidaturaService;
import br.com.freelas.servico.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/candidaturas")
@Tag(name = "Candidaturas")
public class CandidaturaController {

    private final CandidaturaService candidaturas;
    private final ChatService chat;

    public CandidaturaController(CandidaturaService candidaturas, ChatService chat) {
        this.candidaturas = candidaturas;
        this.chat = chat;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar candidatura (o freelancer dono ou a empresa da vaga)")
    public Resposta buscar(@PathVariable Long id) {
        return candidaturas.buscar(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(summary = "Empresa marca a candidatura como RECUSADA")
    public Resposta atualizar(@PathVariable Long id, @Valid @RequestBody AtualizacaoRequest req) {
        return candidaturas.atualizarPelaEmpresa(id, req);
    }

    @PostMapping("/{id}/cancelamento")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Freelancer desiste da candidatura")
    public Resposta cancelar(@PathVariable Long id) {
        return candidaturas.cancelar(id);
    }

    @PostMapping("/{id}/conversa")
    @Operation(summary = "Abrir a conversa da candidatura (201 se criou, 200 se já existia)")
    public ResponseEntity<ConversaResposta> conversa(@PathVariable Long id) {
        var r = chat.abrir(id);
        return Respostas.criadoOuExistente(r.criada(), r.conversa());
    }
}
