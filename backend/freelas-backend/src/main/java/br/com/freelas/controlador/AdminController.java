package br.com.freelas.controlador;

import br.com.freelas.dto.AvaliacaoDto.ContestacaoResposta;
import br.com.freelas.dto.AvaliacaoDto.DecisaoContestacaoRequest;
import br.com.freelas.dto.PagamentoDto;
import br.com.freelas.dto.PaginaResposta;
import br.com.freelas.servico.AvaliacaoService;
import br.com.freelas.servico.PagamentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Moderação: disputas de pagamento e contestações de avaliação. */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMINISTRADOR')")
@Tag(name = "Administração")
public class AdminController {

    private final PagamentoService pagamentos;
    private final AvaliacaoService avaliacoes;

    public AdminController(PagamentoService pagamentos, AvaliacaoService avaliacoes) {
        this.pagamentos = pagamentos;
        this.avaliacoes = avaliacoes;
    }

    @GetMapping("/disputas")
    @Operation(summary = "Pagamentos em disputa")
    public PaginaResposta<PagamentoDto.Resposta> disputas(@RequestParam(defaultValue = "1") @Min(1) int pagina,
                                                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return pagamentos.listarEmDisputa(pagina, tamanho);
    }

    @PostMapping("/disputas/{pagamentoId}/decisao")
    @Operation(summary = "Decidir a disputa: libera ao freelancer ou estorna à empresa")
    public PagamentoDto.Resposta decidirDisputa(@PathVariable Long pagamentoId,
                                                @Valid @RequestBody PagamentoDto.DecisaoDisputaRequest req) {
        return pagamentos.decidirDisputa(pagamentoId, req);
    }

    @GetMapping("/contestacoes")
    @Operation(summary = "Contestações de avaliação abertas")
    public PaginaResposta<ContestacaoResposta> contestacoes(@RequestParam(defaultValue = "1") @Min(1) int pagina,
                                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return avaliacoes.contestacoesAbertas(pagina, tamanho);
    }

    @PostMapping("/contestacoes/{id}/decisao")
    @Operation(summary = "Aceitar (remove a avaliação) ou rejeitar a contestação")
    public ContestacaoResposta decidirContestacao(@PathVariable Long id, @Valid @RequestBody DecisaoContestacaoRequest req) {
        return avaliacoes.decidirContestacao(id, req);
    }
}
