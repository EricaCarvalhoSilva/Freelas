package br.com.freelas.controlador;

import br.com.freelas.dto.AvaliacaoDto;
import br.com.freelas.dto.ContratacaoDto.*;
import br.com.freelas.dto.PagamentoDto;
import br.com.freelas.dto.SubstituicaoDto;
import br.com.freelas.servico.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Contratação: aceite, check-in/out, conclusão, cancelamento, ocorrências, avaliação e reposição. */
@RestController
@RequestMapping("/contratacoes")
@Tag(name = "Contratações")
public class ContratacaoController {

    private final ContratacaoService contratacoes;
    private final AvaliacaoService avaliacoes;
    private final PagamentoService pagamentos;
    private final SubstituicaoService substituicoes;

    public ContratacaoController(ContratacaoService contratacoes, AvaliacaoService avaliacoes,
                                 PagamentoService pagamentos, SubstituicaoService substituicoes) {
        this.contratacoes = contratacoes;
        this.avaliacoes = avaliacoes;
        this.pagamentos = pagamentos;
        this.substituicoes = substituicoes;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar contratação (empresa ou freelancer envolvidos)")
    public Resposta buscar(@PathVariable Long id) {
        return contratacoes.buscar(id);
    }

    @PostMapping("/{id}/aceite")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Freelancer aceita (dentro de 6 h); aqui a empresa é cobrada e o valor fica em custódia")
    public Resposta aceitar(@PathVariable Long id) {
        return contratacoes.aceitar(id);
    }

    @PostMapping("/{id}/recusa")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Freelancer recusa a contratação")
    public Resposta recusar(@PathVariable Long id) {
        return contratacoes.recusar(id);
    }

    @PostMapping("/{id}/check-in")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Registrar chegada (a partir de 60 min antes do início)")
    public Resposta checkin(@PathVariable Long id, @Valid @RequestBody(required = false) CheckinRequest req) {
        return contratacoes.checkin(id, req);
    }

    @PostMapping("/{id}/check-out")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Registrar saída")
    public Resposta checkout(@PathVariable Long id, @Valid @RequestBody(required = false) CheckinRequest req) {
        return contratacoes.checkout(id, req);
    }

    @PostMapping("/{id}/conclusao")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(summary = "Empresa confirma a conclusão e libera o pagamento ao freelancer")
    public Resposta concluir(@PathVariable Long id) {
        return contratacoes.concluir(id);
    }

    @PostMapping("/{id}/cancelamento")
    @PreAuthorize("hasAnyRole('EMPRESA','FREELANCER')")
    @Operation(summary = "Cancelar (com menos de 24 h gera ocorrência; o pagamento é estornado)")
    public Resposta cancelar(@PathVariable Long id, @Valid @RequestBody CancelamentoRequest req) {
        return contratacoes.cancelar(id, req);
    }

    @PostMapping("/{id}/ocorrencias")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(summary = "Registrar ocorrência (no-show, abandono...)")
    public ResponseEntity<OcorrenciaResposta> ocorrencia(@PathVariable Long id, @Valid @RequestBody OcorrenciaRequest req) {
        OcorrenciaResposta r = contratacoes.registrarOcorrencia(id, req);
        return Respostas.criado("/contratacoes/" + id + "/ocorrencias/{id}", r.id(), r);
    }

    @GetMapping("/{id}/pagamento")
    @Operation(summary = "Pagamento da contratação")
    public PagamentoDto.Resposta pagamento(@PathVariable Long id) {
        return pagamentos.consultarDaContratacao(id);
    }

    @PostMapping("/{id}/avaliacoes")
    @PreAuthorize("hasAnyRole('EMPRESA','FREELANCER')")
    @Operation(summary = "Avaliar a outra parte (nota de 0 a 5, janela de 72 h, regra cega)")
    public ResponseEntity<AvaliacaoDto.Resposta> avaliar(@PathVariable Long id, @Valid @RequestBody AvaliacaoDto.Request req) {
        AvaliacaoDto.Resposta r = avaliacoes.avaliar(id, req);
        return Respostas.criado("/contratacoes/" + id + "/avaliacoes/{id}", r.id(), r);
    }

    @GetMapping("/{id}/avaliacoes")
    @Operation(summary = "Avaliações da contratação visíveis para quem consulta")
    public List<AvaliacaoDto.Resposta> avaliacoes(@PathVariable Long id) {
        return avaliacoes.daContratacao(id);
    }

    @PostMapping("/{id}/substituicoes")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(summary = "Pedir reposição do freelancer (vaga relâmpago para a região)")
    public ResponseEntity<SubstituicaoDto.Resposta> substituir(@PathVariable Long id,
                                                               @Valid @RequestBody SubstituicaoDto.Request req) {
        SubstituicaoDto.Resposta r = substituicoes.solicitar(id, req);
        return Respostas.criado("/substituicoes/{id}", r.id(), r);
    }
}
