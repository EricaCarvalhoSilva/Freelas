package br.com.freelas.servico;

import br.com.freelas.entidade.LogAuditoria;
import br.com.freelas.repositorio.LogAuditoriaRepository;
import br.com.freelas.seguranca.UsuarioAtual;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

/** Grava a trilha de auditoria (RN10, RNF04, RNF12). Nunca grava senha, token ou dado bancário completo. */
@Service
public class AuditoriaService {

    private final LogAuditoriaRepository repositorio;
    private final JsonMapper jsonMapper;

    public AuditoriaService(LogAuditoriaRepository repositorio, JsonMapper jsonMapper) {
        this.repositorio = repositorio;
        this.jsonMapper = jsonMapper;
    }

    /** Registra dentro da transação atual (some junto se a operação falhar). */
    @Transactional
    public void registrar(String entidade, Long entidadeId, String acao, Object antes, Object depois) {
        repositorio.save(montar(UsuarioAtual.idOuNulo(), entidade, entidadeId, acao, antes, depois));
    }

    /** Registra mesmo que a operação principal falhe (ex.: tentativa de login recusada). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarIsolado(Long usuarioId, String entidade, Long entidadeId, String acao, Object detalhes) {
        repositorio.save(montar(usuarioId, entidade, entidadeId, acao, null, detalhes));
    }

    private LogAuditoria montar(Long usuarioId, String entidade, Long entidadeId, String acao, Object antes, Object depois) {
        LogAuditoria log = new LogAuditoria();
        log.setUsuarioId(usuarioId);
        log.setEntidade(entidade);
        log.setEntidadeId(entidadeId == null ? 0L : entidadeId);
        log.setAcao(acao);
        log.setDadosAnteriores(json(antes));
        log.setDadosNovos(json(depois));
        log.setIpOrigem(ipDaRequisicao());
        return log;
    }

    private String json(Object valor) {
        return valor == null ? null : jsonMapper.writeValueAsString(valor);
    }

    private static String ipDaRequisicao() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            String encaminhado = req.getHeader("X-Forwarded-For");
            String ip = encaminhado != null ? encaminhado.split(",")[0].trim() : req.getRemoteAddr();
            return ip.length() > 45 ? ip.substring(0, 45) : ip;
        }
        return null;
    }
}
