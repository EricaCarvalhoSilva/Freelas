package br.com.freelas.controlador;

import br.com.freelas.dto.SubstituicaoDto.*;
import br.com.freelas.servico.SubstituicaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/substituicoes")
@Tag(name = "Substituições")
public class SubstituicaoController {

    private final SubstituicaoService substituicoes;

    public SubstituicaoController(SubstituicaoService substituicoes) {
        this.substituicoes = substituicoes;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Acompanhar a reposição")
    public Resposta consultar(@PathVariable Long id) {
        return substituicoes.consultar(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(summary = "Ampliar o raio de busca")
    public Resposta alterarRaio(@PathVariable Long id, @Valid @RequestBody RaioRequest req) {
        return substituicoes.alterarRaio(id, req);
    }

    @PostMapping("/{id}/cancelamento")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(summary = "Cancelar a reposição")
    public Resposta cancelar(@PathVariable Long id) {
        return substituicoes.cancelar(id);
    }

    @PostMapping("/{id}/aceite")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Freelancer aceita a vaga relâmpago (o primeiro que aceitar fica com ela)")
    public Resposta aceitar(@PathVariable Long id) {
        return substituicoes.aceitar(id);
    }
}
