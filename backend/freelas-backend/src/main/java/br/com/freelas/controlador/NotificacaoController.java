package br.com.freelas.controlador;

import br.com.freelas.dto.NotificacaoDto;
import br.com.freelas.dto.PaginaResposta;
import br.com.freelas.seguranca.UsuarioAtual;
import br.com.freelas.servico.NotificacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Notificações")
public class NotificacaoController {

    private final NotificacaoService notificacoes;

    public NotificacaoController(NotificacaoService notificacoes) {
        this.notificacoes = notificacoes;
    }

    @GetMapping("/notificacoes")
    @Operation(summary = "Minhas notificações (filtro opcional por lida)")
    public PaginaResposta<NotificacaoDto> listar(@RequestParam(required = false) Boolean lida,
                                                @RequestParam(defaultValue = "1") @Min(1) int pagina,
                                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return notificacoes.listar(UsuarioAtual.id(), lida, pagina, tamanho);
    }

    @PatchMapping("/notificacoes/{id}")
    @Operation(summary = "Marcar notificação como lida ou não lida")
    public NotificacaoDto marcar(@PathVariable Long id, @Valid @RequestBody NotificacaoDto.LeituraRequest req) {
        return notificacoes.marcarLeitura(UsuarioAtual.id(), id, req.lida());
    }

    @PostMapping("/dispositivos")
    @Operation(summary = "Registrar o aparelho para receber push")
    public ResponseEntity<NotificacaoDto.DispositivoResposta> dispositivo(@Valid @RequestBody NotificacaoDto.DispositivoRequest req) {
        var r = notificacoes.registrarDispositivo(UsuarioAtual.id(), req);
        return Respostas.criado("/dispositivos/{id}", r.id(), r);
    }
}
