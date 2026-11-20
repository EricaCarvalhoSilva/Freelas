package br.com.freelas.controlador;

import br.com.freelas.dto.CarteiraDto.*;
import br.com.freelas.dto.MonetizacaoDto.*;
import br.com.freelas.dto.PaginaResposta;
import br.com.freelas.enums.TipoMovimentacao;
import br.com.freelas.servico.CarteiraService;
import br.com.freelas.servico.MonetizacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Carteira, saques, ganhos, Premium e Boost do freelancer (Endpoints Freelancer V1.1, seções 4, 6 e 7). */
@RestController
@RequestMapping("/freelancers/me")
@PreAuthorize("hasRole('FREELANCER')")
@Tag(name = "Freelancer - carteira, Premium e Boost")
public class FreelancerFinanceiroController {

    private final CarteiraService carteira;
    private final MonetizacaoService monetizacao;

    public FreelancerFinanceiroController(CarteiraService carteira, MonetizacaoService monetizacao) {
        this.carteira = carteira;
        this.monetizacao = monetizacao;
    }

    @GetMapping("/carteira")
    @Operation(summary = "Saldos disponível, pendente e bloqueado")
    public SaldosResposta saldos() {
        return carteira.saldos();
    }

    @GetMapping("/carteira/extrato")
    @Operation(summary = "Extrato com filtro de período (datas AAAA-MM-DD)")
    public PaginaResposta<MovimentacaoResposta> extrato(@RequestParam(required = false) TipoMovimentacao tipo,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                                                        @RequestParam(defaultValue = "1") @Min(1) int pagina,
                                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return carteira.extrato(tipo, de, ate, pagina, tamanho);
    }

    @GetMapping("/ganhos/resumo")
    @Operation(summary = "Painel de ganhos: HOJE, SETE_DIAS, MES ou TOTAL")
    public GanhosResposta ganhos(@RequestParam(required = false) Periodo periodo) {
        return carteira.ganhos(periodo);
    }

    @GetMapping("/dados-recebimento")
    @Operation(summary = "Chaves Pix e contas (mascaradas)")
    public List<ContaRecebimentoResposta> contas() {
        return carteira.listarContas();
    }

    @PostMapping("/dados-recebimento")
    @Operation(summary = "Cadastrar chave Pix ou conta; saques liberados após 24 h")
    public ResponseEntity<ContaRecebimentoResposta> cadastrarConta(@Valid @RequestBody ContaRecebimentoRequest req) {
        var r = carteira.cadastrarConta(req);
        return Respostas.criado("/freelancers/me/dados-recebimento/{id}", r.id(), r);
    }

    @PutMapping("/dados-recebimento/{id}")
    @Operation(summary = "Alterar chave ou conta (reinicia a quarentena de 24 h)")
    public ContaRecebimentoResposta alterarConta(@PathVariable Long id, @Valid @RequestBody ContaRecebimentoRequest req) {
        return carteira.alterarConta(id, req);
    }

    @PostMapping("/saques")
    @Operation(summary = "Solicitar saque Pix do saldo disponível (exige Idempotency-Key)")
    public ResponseEntity<SaqueResposta> sacar(@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 50) String chave,
                                               @Valid @RequestBody SaqueRequest req) {
        var r = carteira.sacar(req, chave);
        return r.criado() ? Respostas.criado("/freelancers/me/saques/{id}", r.saque().id(), r.saque()) : ResponseEntity.ok(r.saque());
    }

    @GetMapping("/saques")
    @Operation(summary = "Listar saques")
    public PaginaResposta<SaqueResposta> saques(@RequestParam(defaultValue = "1") @Min(1) int pagina,
                                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return carteira.listarSaques(pagina, tamanho);
    }

    @GetMapping("/saques/{id}")
    @Operation(summary = "Acompanhar um saque")
    public SaqueResposta saque(@PathVariable Long id) {
        return carteira.consultarSaque(id);
    }

    // ------------------------------------------------------------------ Premium e Boost

    @PostMapping("/assinaturas")
    @Operation(summary = "Assinar o Premium (R$ 19,90 / 30 dias) - exige Idempotency-Key")
    public ResponseEntity<AssinaturaResposta> assinar(@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 50) String chave,
                                                      @Valid @RequestBody AssinaturaFreelancerRequest req) {
        var r = monetizacao.assinarFreelancer(req, chave);
        return Respostas.criadoOuExistente(r.criado(), r.corpo());
    }

    @GetMapping("/assinaturas/atual")
    @Operation(summary = "Assinatura vigente e vencimento")
    public AssinaturaResposta assinaturaAtual() {
        return monetizacao.assinaturaAtual();
    }

    @PostMapping("/assinaturas/atual/cancelamento")
    @Operation(summary = "Cancelar a renovação (acesso até o fim dos 30 dias pagos)")
    public AssinaturaResposta cancelarAssinatura() {
        return monetizacao.cancelarAssinaturaAtual();
    }

    @PostMapping("/boosts")
    @Operation(summary = "Contratar Boost de 24 h (perfil completo, Pix homologado, sem no-show recente)")
    public ResponseEntity<ImpulsionamentoResposta> boost(@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 50) String chave,
                                                         @Valid @RequestBody BoostRequest req) {
        var r = monetizacao.comprarBoost(req, chave);
        return Respostas.criadoOuExistente(r.criado(), r.corpo());
    }

    @GetMapping("/boosts")
    @Operation(summary = "Boosts ativo e anteriores")
    public List<ImpulsionamentoResposta> boosts() {
        return monetizacao.meusBoosts();
    }
}
