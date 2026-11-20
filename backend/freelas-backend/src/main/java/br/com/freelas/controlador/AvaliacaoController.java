package br.com.freelas.controlador;

import br.com.freelas.dto.AvaliacaoDto.*;
import br.com.freelas.servico.AvaliacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/avaliacoes")
@Tag(name = "Avaliações")
public class AvaliacaoController {

    private final AvaliacaoService avaliacoes;

    public AvaliacaoController(AvaliacaoService avaliacoes) {
        this.avaliacoes = avaliacoes;
    }

    @PostMapping("/{id}/contestacoes")
    @Operation(summary = "Contestar uma avaliação recebida (vai para moderação)")
    public ResponseEntity<ContestacaoResposta> contestar(@PathVariable Long id, @Valid @RequestBody ContestacaoRequest req) {
        ContestacaoResposta r = avaliacoes.contestar(id, req);
        return Respostas.criado("/avaliacoes/" + id + "/contestacoes/{id}", r.id(), r);
    }
}
