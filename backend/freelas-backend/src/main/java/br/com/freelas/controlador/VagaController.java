package br.com.freelas.controlador;

import br.com.freelas.dto.CandidaturaDto;
import br.com.freelas.dto.ContratacaoDto;
import br.com.freelas.dto.MonetizacaoDto.DestaqueRequest;
import br.com.freelas.dto.MonetizacaoDto.ImpulsionamentoResposta;
import br.com.freelas.dto.PaginaResposta;
import br.com.freelas.dto.VagaDto.*;
import br.com.freelas.servico.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Vagas (UC03 a UC07 da Empresa e seção 2 do Freelancer). */
@RestController
@Tag(name = "Vagas")
public class VagaController {

    private final VagaService vagas;
    private final CandidaturaService candidaturas;
    private final ContratacaoService contratacoes;
    private final MonetizacaoService monetizacao;

    public VagaController(VagaService vagas, CandidaturaService candidaturas, ContratacaoService contratacoes,
                          MonetizacaoService monetizacao) {
        this.vagas = vagas;
        this.candidaturas = candidaturas;
        this.contratacoes = contratacoes;
        this.monetizacao = monetizacao;
    }

    @GetMapping("/funcoes")
    @Operation(summary = "Funções (categorias) disponíveis para vagas e perfis")
    public List<FuncaoResposta> funcoes() {
        return vagas.listarFuncoes();
    }

    @PostMapping("/vagas")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(summary = "Criar vaga como rascunho")
    public ResponseEntity<Resposta> criar(@Valid @RequestBody CriarRequest req) {
        Resposta r = vagas.criar(req);
        return Respostas.criado("/vagas/{id}", r.id(), r);
    }

    @GetMapping("/vagas")
    @Operation(summary = "Buscar vagas publicadas (destaques primeiro; Premium vê 15 min antes)")
    public PaginaResposta<Resposta> buscar(@RequestParam(required = false) @Positive Integer funcaoId,
                                           @RequestParam(required = false) @Size(max = 100) String cidade,
                                           @RequestParam(required = false) @Pattern(regexp = "[A-Za-z]{2}", message = "deve ter 2 letras") String uf,
                                           @RequestParam(required = false) @Pattern(regexp = "\\d{1,8}", message = "deve ter de 1 a 8 dígitos") String cepPrefixo,
                                           @RequestParam(required = false) @DecimalMin("0.0") BigDecimal valorMin,
                                           @RequestParam(required = false) @DecimalMin("0.0") BigDecimal valorMax,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicioDe,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicioAte,
                                           @RequestParam(required = false) OrdenacaoBusca ordenarPor,
                                           @RequestParam(required = false) Sort.Direction ordem,
                                           @RequestParam(defaultValue = "1") @Min(1) int pagina,
                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return vagas.buscarAbertas(funcaoId, cidade, uf, cepPrefixo, valorMin, valorMax, inicioDe, inicioAte,
                ordenarPor, ordem, pagina, tamanho);
    }

    @GetMapping("/vagas/{id}")
    @Operation(summary = "Detalhe da vaga")
    public Resposta detalhe(@PathVariable Long id) {
        return vagas.buscar(id);
    }

    @PatchMapping("/vagas/{id}")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(summary = "Editar vaga (rascunho livre; publicada só antes da primeira candidatura)")
    public Resposta atualizar(@PathVariable Long id, @Valid @RequestBody AtualizarRequest req) {
        return vagas.atualizar(id, req);
    }

    @DeleteMapping("/vagas/{id}")
    @PreAuthorize("hasRole('EMPRESA')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Excluir rascunho")
    public void excluir(@PathVariable Long id) {
        vagas.excluir(id);
    }

    @PostMapping("/vagas/{id}/publicacao")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(summary = "Publicar vaga (valida regras e avisa freelancers da região pela fila)")
    public Resposta publicar(@PathVariable Long id) {
        return vagas.publicar(id);
    }

    @GetMapping("/vagas/{id}/historico")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(summary = "Histórico de alterações da vaga (log de auditoria)")
    public PaginaResposta<HistoricoItem> historico(@PathVariable Long id,
                                                   @RequestParam(defaultValue = "1") @Min(1) int pagina,
                                                   @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return vagas.historico(id, pagina, tamanho);
    }

    @PostMapping("/vagas/{id}/cancelamento")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(summary = "Cancelar vaga (recusa candidaturas pendentes e estorna pagamentos)")
    public Resposta cancelar(@PathVariable Long id, @Valid @RequestBody CancelamentoRequest req) {
        return vagas.cancelar(id, req);
    }

    // ------------------------------------------------------------------ candidaturas e contratação

    @PostMapping("/vagas/{id}/candidaturas")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Candidatar-se à vaga")
    public ResponseEntity<CandidaturaDto.Resposta> candidatar(@PathVariable Long id) {
        CandidaturaDto.Resposta r = candidaturas.candidatar(id);
        return Respostas.criado("/candidaturas/{id}", r.id(), r);
    }

    @GetMapping("/vagas/{id}/candidaturas")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(summary = "Candidatos da vaga, ordenados por score, trabalhos concluídos ou data")
    public PaginaResposta<CandidaturaDto.CandidatoResposta> candidatos(@PathVariable Long id,
                                                                      @RequestParam(required = false) CandidaturaDto.Ordenacao ordenarPor,
                                                                      @RequestParam(required = false) Sort.Direction ordem,
                                                                      @RequestParam(defaultValue = "1") @Min(1) int pagina,
                                                                      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return candidaturas.candidatosDaVaga(id, ordenarPor, ordem, pagina, tamanho);
    }

    @PostMapping("/vagas/{id}/contratacoes")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(summary = "Contratar um candidato (o freelancer tem 6 h para aceitar; a cobrança acontece no aceite)")
    public ResponseEntity<ContratacaoDto.Resposta> contratar(@PathVariable Long id,
                                                             @Valid @RequestBody ContratacaoDto.ContratarRequest req) {
        ContratacaoDto.Resposta r = contratacoes.contratar(id, req);
        return Respostas.criado("/contratacoes/{id}", r.id(), r);
    }

    // ------------------------------------------------------------------ destaque (Premium/Boost da empresa)

    @PostMapping("/vagas/{id}/destaques")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(summary = "Comprar destaque para a vaga (exige Idempotency-Key)")
    public ResponseEntity<ImpulsionamentoResposta> destacar(@PathVariable Long id,
                                                            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 50) String chave,
                                                            @Valid @RequestBody DestaqueRequest req) {
        var r = monetizacao.destacarVaga(id, req, chave);
        return Respostas.criadoOuExistente(r.criado(), r.corpo());
    }

    @GetMapping("/vagas/{id}/destaques")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(summary = "Destaques comprados para a vaga")
    public List<ImpulsionamentoResposta> destaques(@PathVariable Long id) {
        return monetizacao.destaquesDaVaga(id);
    }
}
