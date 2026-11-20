package br.com.freelas.controlador;

import br.com.freelas.dto.*;
import br.com.freelas.dto.AutenticacaoDto.AlterarSenhaRequest;
import br.com.freelas.dto.FreelancerDto.*;
import br.com.freelas.enums.StatusCandidatura;
import br.com.freelas.enums.StatusContratacao;
import br.com.freelas.servico.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Perfil Freelancer (Endpoints Freelancer V1.1, seções 1, 2, 3, 5 e 9), sem o prefixo /api/v1,
 * como recomenda o Padrão da API. Os recursos do usuário logado ficam em /freelancers/me.
 */
@RestController
@RequestMapping("/freelancers")
@Tag(name = "Freelancer")
public class FreelancerController {

    private final FreelancerService freelancers;
    private final CandidaturaService candidaturas;
    private final VagaService vagas;
    private final ContratacaoService contratacoes;
    private final SubstituicaoService substituicoes;
    private final AvaliacaoService avaliacoes;
    private final ChatService chat;

    public FreelancerController(FreelancerService freelancers, CandidaturaService candidaturas, VagaService vagas,
                                ContratacaoService contratacoes, SubstituicaoService substituicoes,
                                AvaliacaoService avaliacoes, ChatService chat) {
        this.freelancers = freelancers;
        this.candidaturas = candidaturas;
        this.vagas = vagas;
        this.contratacoes = contratacoes;
        this.substituicoes = substituicoes;
        this.avaliacoes = avaliacoes;
        this.chat = chat;
    }

    @PostMapping
    @Operation(summary = "Cadastrar freelancer (18+ anos, aceite dos termos); cria a carteira")
    public ResponseEntity<Resposta> cadastrar(@Valid @RequestBody CadastroRequest req) {
        Resposta r = freelancers.cadastrar(req);
        return Respostas.criado("/freelancers/{id}", r.id(), r);
    }

    // ------------------------------------------------------------------ visão da empresa

    @GetMapping("/{id}")
    @Operation(summary = "Perfil do candidato para a empresa (sem CPF; contato só após contratação confirmada)")
    public PerfilParaEmpresa perfil(@PathVariable Long id) {
        return freelancers.perfilParaEmpresa(id);
    }

    @GetMapping("/{id}/avaliacoes")
    @Operation(summary = "Avaliações publicadas recebidas pelo freelancer")
    public PaginaResposta<AvaliacaoDto.Resposta> avaliacoesDoFreelancer(@PathVariable Long id,
                                                                       @RequestParam(defaultValue = "1") @Min(1) int pagina,
                                                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return avaliacoes.recebidasPorFreelancer(id, pagina, tamanho);
    }

    // ------------------------------------------------------------------ meu perfil

    @GetMapping("/me")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Consultar o próprio perfil")
    public Resposta meuPerfil() {
        return freelancers.meuPerfil();
    }

    @PatchMapping("/me")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Alterar dados livres: telefone, apresentação e endereço")
    public Resposta atualizar(@Valid @RequestBody AtualizacaoRequest req) {
        return freelancers.atualizar(req);
    }

    @PutMapping("/me/foto")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Enviar ou trocar a foto (URL no armazenamento de arquivos)")
    public Resposta foto(@Valid @RequestBody FotoRequest req) {
        return freelancers.alterarFoto(req);
    }

    @PutMapping("/me/curriculo")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Enviar ou trocar o currículo (URL no armazenamento de arquivos)")
    public Resposta curriculo(@Valid @RequestBody CurriculoRequest req) {
        return freelancers.alterarCurriculo(req);
    }

    @PutMapping("/me/funcoes")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Definir as funções (categorias de atuação) e os anos de experiência")
    public List<FuncaoResposta> funcoes(@Valid @RequestBody FuncoesRequest req) {
        return freelancers.definirFuncoes(req);
    }

    @PostMapping("/me/formacoes")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Adicionar formação ou curso")
    public ResponseEntity<FormacaoResposta> adicionarFormacao(@Valid @RequestBody FormacaoRequest req) {
        FormacaoResposta r = freelancers.adicionarFormacao(req);
        return Respostas.criado("/freelancers/me/formacoes/{id}", r.id(), r);
    }

    @DeleteMapping("/me/formacoes/{formacaoId}")
    @PreAuthorize("hasRole('FREELANCER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remover formação")
    public void removerFormacao(@PathVariable Long formacaoId) {
        freelancers.removerFormacao(formacaoId);
    }

    @PatchMapping("/me/senha")
    @PreAuthorize("hasRole('FREELANCER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Alterar a senha")
    public void senha(@Valid @RequestBody AlterarSenhaRequest req) {
        freelancers.alterarSenha(req);
    }

    @GetMapping("/me/completude")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "O que falta no perfil")
    public CompletudeResposta completude() {
        return freelancers.completude();
    }

    @DeleteMapping("/me")
    @PreAuthorize("hasRole('FREELANCER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Pedir a exclusão da conta (LGPD)")
    public void excluir() {
        freelancers.excluir();
    }

    // ------------------------------------------------------------------ candidaturas e favoritos

    @GetMapping("/me/candidaturas")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Minhas candidaturas")
    public PaginaResposta<CandidaturaDto.Resposta> candidaturas(@RequestParam(required = false) StatusCandidatura status,
                                                               @RequestParam(defaultValue = "1") @Min(1) int pagina,
                                                               @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return candidaturas.minhas(status, pagina, tamanho);
    }

    @PutMapping("/me/favoritos/{vagaId}")
    @PreAuthorize("hasRole('FREELANCER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Favoritar vaga (não reserva a vaga)")
    public void favoritar(@PathVariable Long vagaId) {
        vagas.favoritar(vagaId);
    }

    @DeleteMapping("/me/favoritos/{vagaId}")
    @PreAuthorize("hasRole('FREELANCER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remover dos favoritos")
    public void desfavoritar(@PathVariable Long vagaId) {
        vagas.desfavoritar(vagaId);
    }

    @GetMapping("/me/favoritos")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Vagas favoritas")
    public PaginaResposta<VagaDto.Resposta> favoritos(@RequestParam(defaultValue = "1") @Min(1) int pagina,
                                                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return vagas.listarFavoritas(pagina, tamanho);
    }

    // ------------------------------------------------------------------ contratações e reposição

    @GetMapping("/me/contratacoes")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Minhas contratações (turnos)")
    public PaginaResposta<ContratacaoDto.Resposta> contratacoes(@RequestParam(required = false) StatusContratacao status,
                                                               @RequestParam(defaultValue = "1") @Min(1) int pagina,
                                                               @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return contratacoes.listarDoFreelancer(status, pagina, tamanho);
    }

    @GetMapping("/me/convites-reposicao")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Vagas relâmpago (substituições) abertas para mim")
    public List<SubstituicaoDto.Resposta> convites() {
        return substituicoes.convites();
    }

    // ------------------------------------------------------------------ avaliação e score

    @GetMapping("/me/avaliacoes/pendentes")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Turnos com a janela de 72 h aberta para avaliar a empresa")
    public List<AvaliacaoDto.PendenteResposta> pendentes() {
        return avaliacoes.pendentesDoFreelancer();
    }

    @GetMapping("/me/avaliacoes")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Avaliações que recebi (as ocultas pela regra cega não aparecem)")
    public PaginaResposta<AvaliacaoDto.Resposta> minhasAvaliacoes(@RequestParam(defaultValue = "1") @Min(1) int pagina,
                                                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return avaliacoes.recebidasPorFreelancer(br.com.freelas.seguranca.UsuarioAtual.id(), pagina, tamanho);
    }

    @GetMapping("/me/score")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Score, nível de reputação e a composição do cálculo")
    public AvaliacaoDto.ScoreResposta score() {
        return avaliacoes.scoreDoFreelancer();
    }

    @GetMapping("/me/conversas")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Minhas conversas com empresas")
    public PaginaResposta<ChatDto.ConversaResposta> conversas(@RequestParam(defaultValue = "1") @Min(1) int pagina,
                                                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return chat.listar(pagina, tamanho);
    }
}
