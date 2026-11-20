package br.com.freelas.controlador;

import br.com.freelas.dto.PagamentoDto.*;
import br.com.freelas.excecao.RegraNegocioException;
import br.com.freelas.servico.PagamentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@Tag(name = "Pagamentos")
public class PagamentoController {

    private final PagamentoService pagamentos;
    private final Validator validator;

    public PagamentoController(PagamentoService pagamentos, Validator validator) {
        this.pagamentos = pagamentos;
        this.validator = validator;
    }

    @PostMapping("/pagamentos/{id}/disputa")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(summary = "Empresa contesta o pagamento antes da liberação (fica retido até a decisão)")
    public Resposta disputa(@PathVariable Long id, @Valid @RequestBody DisputaRequest req) {
        return pagamentos.abrirDisputa(id, req);
    }

    /**
     * Webhook do gateway. O corpo chega cru para conferir a assinatura HMAC (cabeçalho X-Assinatura)
     * exatamente sobre os bytes recebidos; só depois é convertido e validado.
     */
    @PostMapping(value = "/webhooks/gateway-pagamento", consumes = "application/json")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Webhook do gateway de pagamento (assinado com HMAC-SHA256)")
    public void webhook(@RequestHeader("X-Assinatura") String assinatura, @RequestBody String corpo) {
        WebhookRequest evento;
        try {
            evento = pagamentos.jsonMapper().readValue(corpo, WebhookRequest.class);
        } catch (RuntimeException e) {
            throw new RegraNegocioException("Corpo do webhook inválido.");
        }
        Set<ConstraintViolation<WebhookRequest>> erros = validator.validate(evento);
        if (!erros.isEmpty()) {
            throw new ConstraintViolationException(erros);
        }
        pagamentos.receberWebhook(corpo, assinatura, evento);
    }
}
