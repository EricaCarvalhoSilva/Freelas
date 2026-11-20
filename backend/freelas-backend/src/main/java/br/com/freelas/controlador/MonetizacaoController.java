package br.com.freelas.controlador;

import br.com.freelas.dto.MonetizacaoDto.*;
import br.com.freelas.enums.PublicoPlano;
import br.com.freelas.enums.TipoImpulsionamento;
import br.com.freelas.servico.MonetizacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Planos Premium e opções de destaque/Boost (catálogo público) e gestão da assinatura. */
@RestController
@Tag(name = "Premium e Boost")
public class MonetizacaoController {

    private final MonetizacaoService monetizacao;

    public MonetizacaoController(MonetizacaoService monetizacao) {
        this.monetizacao = monetizacao;
    }

    @GetMapping("/planos")
    @Operation(summary = "Planos Premium (filtro por público: EMPRESA ou FREELANCER)")
    public List<PlanoResposta> planos(@RequestParam(required = false) PublicoPlano publico) {
        return monetizacao.listarPlanos(publico);
    }

    @GetMapping("/opcoes-destaque")
    @Operation(summary = "Opções de destaque de vaga e preços")
    public List<OpcaoResposta> opcoesDestaque() {
        return monetizacao.opcoes(TipoImpulsionamento.DESTAQUE_VAGA);
    }

    @GetMapping("/boosts/opcoes")
    @Operation(summary = "Opções de Boost de perfil e preços")
    public List<OpcaoResposta> opcoesBoost() {
        return monetizacao.opcoes(TipoImpulsionamento.BOOST_PERFIL);
    }

    @PatchMapping("/assinaturas/{id}")
    @Operation(summary = "Ligar ou desligar a renovação automática")
    public AssinaturaResposta renovacao(@PathVariable Long id, @Valid @RequestBody RenovacaoRequest req) {
        return monetizacao.alterarRenovacao(id, req);
    }

    @PostMapping("/assinaturas/{id}/cancelamento")
    @Operation(summary = "Cancelar a assinatura (vale até o fim do período pago)")
    public AssinaturaResposta cancelar(@PathVariable Long id) {
        return monetizacao.cancelarAssinatura(id);
    }
}
