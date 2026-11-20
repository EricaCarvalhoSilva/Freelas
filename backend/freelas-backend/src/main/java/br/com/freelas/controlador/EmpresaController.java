package br.com.freelas.controlador;

import br.com.freelas.dto.*;
import br.com.freelas.dto.AutenticacaoDto.AlterarSenhaRequest;
import br.com.freelas.dto.EmpresaDto.*;
import br.com.freelas.dto.MonetizacaoDto.AssinaturaEmpresaRequest;
import br.com.freelas.dto.MonetizacaoDto.AssinaturaResposta;
import br.com.freelas.enums.StatusContratacao;
import br.com.freelas.enums.StatusPagamento;
import br.com.freelas.enums.StatusVaga;
import br.com.freelas.servico.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/** Perfil Empresa: cadastro, perfil, meios de pagamento e listas da empresa (Endpoints Empresa 2.1 a 2.11). */
@RestController
@RequestMapping("/empresas")
@Tag(name = "Empresa")
public class EmpresaController {

    private final EmpresaService empresas;
    private final VagaService vagas;
    private final ContratacaoService contratacoes;
    private final PagamentoService pagamentos;
    private final AvaliacaoService avaliacoes;
    private final ChatService chat;
    private final MonetizacaoService monetizacao;

    public EmpresaController(EmpresaService empresas, VagaService vagas, ContratacaoService contratacoes,
                             PagamentoService pagamentos, AvaliacaoService avaliacoes, ChatService chat,
                             MonetizacaoService monetizacao) {
        this.empresas = empresas;
        this.vagas = vagas;
        this.contratacoes = contratacoes;
        this.pagamentos = pagamentos;
        this.avaliacoes = avaliacoes;
        this.chat = chat;
        this.monetizacao = monetizacao;
    }

    @PostMapping
    @Operation(summary = "Criar o cadastro da empresa (status PENDENTE até confirmar o e-mail)")
    public ResponseEntity<Resposta> cadastrar(@Valid @RequestBody CadastroRequest req) {
        Resposta r = empresas.cadastrar(req);
        return Respostas.criado("/empresas/{id}", r.id(), r);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar a empresa (completo para a própria empresa, público para os demais)")
    public Object buscar(@PathVariable Long id) {
        return empresas.buscar(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(summary = "Alterar dados do perfil (CNPJ só pelo suporte; e-mail só depois de confirmar)")
    public Resposta atualizar(@PathVariable Long id, @Valid @RequestBody AtualizacaoRequest req) {
        return empresas.atualizar(id, req);
    }

    @PutMapping("/{id}/endereco")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(summary = "Substituir o endereço do estabelecimento")
    public Resposta alterarEndereco(@PathVariable Long id, @Valid @RequestBody EnderecoDto req) {
        return empresas.alterarEndereco(id, req);
    }

    @PutMapping("/{id}/senha")
    @PreAuthorize("hasRole('EMPRESA')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Alterar a senha informando a atual")
    public void alterarSenha(@PathVariable Long id, @Valid @RequestBody AlterarSenhaRequest req) {
        empresas.alterarSenha(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('EMPRESA')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Pedir a exclusão da conta (LGPD)")
    public void excluir(@PathVariable Long id) {
        empresas.excluir(id);
    }

    // ------------------------------------------------------------------ meios de pagamento

    @GetMapping("/{id}/meios-pagamento")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(summary = "Listar os meios de pagamento")
    public List<MeioPagamentoResposta> listarMeios(@PathVariable Long id) {
        return empresas.listarMeios(id);
    }

    @PostMapping("/{id}/meios-pagamento")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(summary = "Cadastrar cartão ou Pix (só o token do gateway, nunca o número do cartão)")
    public ResponseEntity<MeioPagamentoResposta> cadastrarMeio(@PathVariable Long id, @Valid @RequestBody MeioPagamentoRequest req) {
        MeioPagamentoResposta r = empresas.cadastrarMeio(id, req);
        return Respostas.criado("/empresas/" + id + "/meios-pagamento/{meioId}", r.id(), r);
    }

    @DeleteMapping("/{id}/meios-pagamento/{meioId}")
    @PreAuthorize("hasRole('EMPRESA')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desativar um meio de pagamento")
    public void desativarMeio(@PathVariable Long id, @PathVariable Long meioId) {
        empresas.desativarMeio(id, meioId);
    }

    // ------------------------------------------------------------------ listas da empresa

    @GetMapping("/{id}/vagas")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(summary = "Listar as vagas da empresa")
    public PaginaResposta<VagaDto.Resposta> vagas(@PathVariable Long id, @RequestParam(required = false) StatusVaga status,
                                                 @RequestParam(defaultValue = "1") @Min(1) int pagina,
                                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return vagas.listarDaEmpresa(id, status, pagina, tamanho);
    }

    @GetMapping("/{id}/contratacoes")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(summary = "Listar as contratações da empresa")
    public PaginaResposta<ContratacaoDto.Resposta> contratacoes(@PathVariable Long id,
                                                               @RequestParam(required = false) StatusContratacao status,
                                                               @RequestParam(required = false) Long vagaId,
                                                               @RequestParam(defaultValue = "1") @Min(1) int pagina,
                                                               @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return contratacoes.listarDaEmpresa(id, status, vagaId, pagina, tamanho);
    }

    @GetMapping("/{id}/pagamentos")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(summary = "Histórico de pagamentos e comprovantes")
    public PaginaResposta<PagamentoDto.Resposta> pagamentos(@PathVariable Long id,
                                                           @RequestParam(required = false) StatusPagamento status,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime de,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime ate,
                                                           @RequestParam(defaultValue = "1") @Min(1) int pagina,
                                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return pagamentos.listarDaEmpresa(id, status, de, ate, pagina, tamanho);
    }

    @GetMapping("/{id}/avaliacoes")
    @Operation(summary = "Avaliações recebidas pela empresa (só leitura)")
    public PaginaResposta<AvaliacaoDto.Resposta> avaliacoes(@PathVariable Long id,
                                                           @RequestParam(defaultValue = "1") @Min(1) int pagina,
                                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        empresas.carregar(id);
        return avaliacoes.recebidasPorEmpresa(id, pagina, tamanho);
    }

    @GetMapping("/{id}/conversas")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(summary = "Conversas da empresa com a última mensagem e as não lidas")
    public PaginaResposta<ChatDto.ConversaResposta> conversas(@PathVariable Long id,
                                                             @RequestParam(defaultValue = "1") @Min(1) int pagina,
                                                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return chat.listarDaEmpresa(id, pagina, tamanho);
    }

    @PostMapping("/{id}/assinaturas")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(summary = "Assinar o Premium da empresa (exige Idempotency-Key)")
    public ResponseEntity<AssinaturaResposta> assinar(@PathVariable Long id,
                                                      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 50) String chave,
                                                      @Valid @RequestBody AssinaturaEmpresaRequest req) {
        var r = monetizacao.assinarEmpresa(id, req, chave);
        return Respostas.criadoOuExistente(r.criado(), r.corpo());
    }

    @GetMapping("/{id}/assinaturas")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(summary = "Assinatura atual e histórico")
    public List<AssinaturaResposta> assinaturas(@PathVariable Long id) {
        return monetizacao.listarAssinaturas(id);
    }
}
