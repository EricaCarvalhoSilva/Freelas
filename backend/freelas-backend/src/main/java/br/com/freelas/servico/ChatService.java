package br.com.freelas.servico;

import br.com.freelas.dto.ChatDto.*;
import br.com.freelas.dto.ContratacaoDto;
import br.com.freelas.dto.PaginaResposta;
import br.com.freelas.entidade.*;
import br.com.freelas.enums.StatusContratacao;
import br.com.freelas.enums.TipoUsuario;
import br.com.freelas.excecao.*;
import br.com.freelas.repositorio.*;
import br.com.freelas.seguranca.UsuarioAtual;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Chat (UC10). A mensagem é gravada na hora; o aviso vai por notificação.
 * Até a contratação ser confirmada, telefone e e-mail digitados no chat são mascarados (KAN-70 seção 2).
 * A entrega em tempo real por WebSocket (documento da Helen) pode ser ligada em cima deste serviço.
 */
@Service
public class ChatService {

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");
    private static final Pattern TELEFONE = Pattern.compile("(\\+?55\\s?)?\\(?\\d{2}\\)?\\s?9?\\d{4}[\\s.-]?\\d{4}");
    private static final Set<StatusContratacao> CONTATO_LIBERADO =
            Set.of(StatusContratacao.CONFIRMADA, StatusContratacao.EM_ANDAMENTO, StatusContratacao.CONCLUIDA);

    private final ConversaRepository conversas;
    private final MensagemRepository mensagens;
    private final CandidaturaRepository candidaturas;
    private final ContratacaoRepository contratacoes;
    private final UsuarioRepository usuarios;
    private final NotificacaoService notificacoes;

    public ChatService(ConversaRepository conversas, MensagemRepository mensagens, CandidaturaRepository candidaturas,
                       ContratacaoRepository contratacoes, UsuarioRepository usuarios, NotificacaoService notificacoes) {
        this.conversas = conversas;
        this.mensagens = mensagens;
        this.candidaturas = candidaturas;
        this.contratacoes = contratacoes;
        this.usuarios = usuarios;
        this.notificacoes = notificacoes;
    }

    /** A empresa abre a conversa com o candidato; se já existir, devolve a mesma. */
    @Transactional
    public AbrirResultado abrir(Long candidaturaId) {
        Candidatura c = candidaturas.findById(candidaturaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Candidatura", candidaturaId));
        Long usuario = UsuarioAtual.id();
        if (!c.getVaga().getEmpresa().getId().equals(usuario) && !c.getFreelancer().getId().equals(usuario)) {
            throw new AcessoNegadoException("Só a empresa da vaga e o candidato participam desta conversa.");
        }
        var existente = conversas.findByCandidaturaId(candidaturaId);
        if (existente.isPresent()) return new AbrirResultado(resposta(existente.get()), false);
        Conversa conversa = new Conversa();
        conversa.setCandidatura(c);
        conversas.save(conversa);
        return new AbrirResultado(resposta(conversa), true);
    }

    @Transactional(readOnly = true)
    public PaginaResposta<ConversaResposta> listar(int pagina, int tamanho) {
        Long id = UsuarioAtual.id();
        var pageable = Paginas.de(pagina, tamanho);
        var page = UsuarioAtual.perfil() == TipoUsuario.EMPRESA
                ? conversas.listarDaEmpresa(id, pageable) : conversas.listarDoFreelancer(id, pageable);
        return PaginaResposta.de(page, this::resposta);
    }

    @Transactional(readOnly = true)
    public PaginaResposta<ConversaResposta> listarDaEmpresa(Long empresaId, int pagina, int tamanho) {
        Contas.exigirMesmoUsuario(empresaId, UsuarioAtual.id());
        return listar(pagina, tamanho);
    }

    @Transactional(readOnly = true)
    public List<MensagemResposta> mensagens(Long conversaId, LocalDateTime antesDe, int tamanho) {
        participante(conversaId);
        return mensagens.listar(conversaId, antesDe == null ? LocalDateTime.now().plusYears(100) : antesDe, PageRequest.of(0, tamanho)).stream().map(MensagemResposta::de).toList();
    }

    @Transactional
    public EnvioResultado enviar(Long conversaId, MensagemRequest req) {
        Conversa conversa = participante(conversaId);
        Long remetente = UsuarioAtual.id();
        if (req.idCliente() != null) {
            var repetida = mensagens.findByConversaIdAndIdCliente(conversaId, req.idCliente());
            if (repetida.isPresent()) return new EnvioResultado(MensagemResposta.de(repetida.get()), false);
        }
        Candidatura cand = conversa.getCandidatura();
        boolean contatoLiberado = contratacoes.findByCandidaturaId(cand.getId())
                .map(c -> CONTATO_LIBERADO.contains(c.getStatus())).orElse(false);
        String conteudo = req.conteudo().trim();
        if (!contatoLiberado) {
            conteudo = EMAIL.matcher(conteudo).replaceAll("[contato oculto até a contratação]");
            conteudo = TELEFONE.matcher(conteudo).replaceAll("[contato oculto até a contratação]");
        }
        Mensagem m = new Mensagem();
        m.setConversa(conversa);
        m.setRemetente(usuarios.getReferenceById(remetente));
        m.setConteudo(conteudo);
        m.setIdCliente(req.idCliente());
        mensagens.save(m);

        Long destinatario = cand.getFreelancer().getId().equals(remetente) ? cand.getVaga().getEmpresa().getId() : cand.getFreelancer().getId();
        notificacoes.notificar(destinatario, "MENSAGEM", "Nova mensagem sobre a vaga de " + cand.getVaga().getFuncao().getNome() + ".", conversaId);
        return new EnvioResultado(MensagemResposta.de(m), true);
    }

    @Transactional
    public LeituraResposta marcarLidas(Long conversaId) {
        participante(conversaId);
        return new LeituraResposta(mensagens.marcarComoLidas(conversaId, UsuarioAtual.id(), LocalDateTime.now()));
    }

    // ------------------------------------------------------------------ auxiliares

    private Conversa participante(Long conversaId) {
        Conversa c = conversas.findById(conversaId).orElseThrow(() -> new RecursoNaoEncontradoException("Conversa", conversaId));
        Long id = UsuarioAtual.id();
        Candidatura cand = c.getCandidatura();
        if (!cand.getFreelancer().getId().equals(id) && !cand.getVaga().getEmpresa().getId().equals(id)) {
            throw new AcessoNegadoException("Você não participa desta conversa.");
        }
        return c;
    }

    private ConversaResposta resposta(Conversa c) {
        Candidatura cand = c.getCandidatura();
        Vaga v = cand.getVaga();
        Long logado = UsuarioAtual.id();
        return new ConversaResposta(c.getId(), cand.getId(), v.getId(), v.getFuncao().getNome(),
                new ContratacaoDto.Parte(v.getEmpresa().getId(), v.getEmpresa().getNomeFantasia()),
                new ContratacaoDto.Parte(cand.getFreelancer().getId(), cand.getFreelancer().getNome()),
                mensagens.findFirstByConversaIdOrderByEnviadaEmDescIdDesc(c.getId()).map(MensagemResposta::de).orElse(null),
                mensagens.countByConversaIdAndRemetenteIdNotAndLidaEmIsNull(c.getId(), logado), c.getCriadaEm());
    }

    public record AbrirResultado(ConversaResposta conversa, boolean criada) {}

    public record EnvioResultado(MensagemResposta mensagem, boolean criada) {}
}
