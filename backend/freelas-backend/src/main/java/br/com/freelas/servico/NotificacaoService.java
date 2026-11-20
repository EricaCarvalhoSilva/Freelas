package br.com.freelas.servico;

import br.com.freelas.dto.NotificacaoDto;
import br.com.freelas.dto.PaginaResposta;
import br.com.freelas.entidade.DispositivoPush;
import br.com.freelas.entidade.Notificacao;
import br.com.freelas.excecao.RecursoNaoEncontradoException;
import br.com.freelas.repositorio.DispositivoPushRepository;
import br.com.freelas.repositorio.NotificacaoRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Notificações dentro do app (RNF13) e registro de aparelhos para push. */
@Service
public class NotificacaoService {

    private final NotificacaoRepository notificacoes;
    private final DispositivoPushRepository dispositivos;

    public NotificacaoService(NotificacaoRepository notificacoes, DispositivoPushRepository dispositivos) {
        this.notificacoes = notificacoes;
        this.dispositivos = dispositivos;
    }

    @Transactional
    public void notificar(Long usuarioId, String tipo, String mensagem, Long referenciaId) {
        Notificacao n = new Notificacao();
        n.setUsuarioId(usuarioId);
        n.setTipo(tipo);
        n.setMensagem(mensagem.length() > 255 ? mensagem.substring(0, 255) : mensagem);
        n.setReferenciaId(referenciaId);
        notificacoes.save(n);
        // O envio do push para os aparelhos (dispositivo_push) seria feito por um provedor externo.
    }

    @Transactional(readOnly = true)
    public PaginaResposta<NotificacaoDto> listar(Long usuarioId, Boolean lida, int pagina, int tamanho) {
        var pageable = Paginas.de(pagina, tamanho, Sort.by(Sort.Direction.DESC, "criadaEm"));
        var page = lida == null ? notificacoes.findByUsuarioId(usuarioId, pageable)
                : notificacoes.findByUsuarioIdAndLida(usuarioId, lida, pageable);
        return PaginaResposta.de(page, NotificacaoDto::de);
    }

    @Transactional
    public NotificacaoDto marcarLeitura(Long usuarioId, Long notificacaoId, boolean lida) {
        Notificacao n = notificacoes.findByIdAndUsuarioId(notificacaoId, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Notificação", notificacaoId));
        n.setLida(lida);
        return NotificacaoDto.de(n);
    }

    /** Registra (ou reativa) o token do aparelho para push. */
    @Transactional
    public NotificacaoDto.DispositivoResposta registrarDispositivo(Long usuarioId, NotificacaoDto.DispositivoRequest req) {
        DispositivoPush d = dispositivos.findByTokenPush(req.tokenPush()).orElseGet(DispositivoPush::new);
        d.setUsuarioId(usuarioId);
        d.setTokenPush(req.tokenPush());
        d.setPlataforma(req.plataforma());
        d.setAtivo(true);
        d.setRegistradoEm(LocalDateTime.now());
        dispositivos.save(d);
        return new NotificacaoDto.DispositivoResposta(d.getId(), d.getPlataforma(), d.getRegistradoEm());
    }
}
