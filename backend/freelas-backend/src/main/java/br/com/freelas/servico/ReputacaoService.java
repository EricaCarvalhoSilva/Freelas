package br.com.freelas.servico;

import br.com.freelas.entidade.Contratacao;
import br.com.freelas.entidade.Empresa;
import br.com.freelas.entidade.Freelancer;
import br.com.freelas.entidade.Ocorrencia;
import br.com.freelas.entidade.Usuario;
import br.com.freelas.enums.NivelReputacao;
import br.com.freelas.enums.TipoOcorrencia;
import br.com.freelas.repositorio.AvaliacaoRepository;
import br.com.freelas.repositorio.OcorrenciaRepository;
import br.com.freelas.repositorio.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Score de 0 a 5 (Definição de regras de avaliação, score e reputação):
 * média das notas visíveis dos últimos 90 dias menos as penalidades das ocorrências do mesmo período.
 * Sem nenhuma avaliação, o score fica 0 e o nível fica vazio ("sem avaliações").
 */
@Service
public class ReputacaoService {

    public static final int JANELA_DIAS = 90;

    private final AvaliacaoRepository avaliacoes;
    private final OcorrenciaRepository ocorrencias;
    private final UsuarioRepository usuarios;

    public ReputacaoService(AvaliacaoRepository avaliacoes, OcorrenciaRepository ocorrencias, UsuarioRepository usuarios) {
        this.avaliacoes = avaliacoes;
        this.ocorrencias = ocorrencias;
        this.usuarios = usuarios;
    }

    /** Penalidade de cada ocorrência (proposta; os documentos só dizem "severa" para no-show). */
    public static BigDecimal impacto(TipoOcorrencia tipo) {
        return switch (tipo) {
            case NO_SHOW -> new BigDecimal("1.00");
            case ABANDONO_TURNO -> new BigDecimal("0.75");
            case CANCELAMENTO_TARDIO -> new BigDecimal("0.50");
            case CANCELAMENTO_PREVIO -> new BigDecimal("0.10");
        };
    }

    @Transactional
    public Ocorrencia registrarOcorrencia(Contratacao c, Usuario causador, TipoOcorrencia tipo) {
        Ocorrencia o = new Ocorrencia();
        o.setContratacao(c);
        o.setUsuario(causador);
        o.setTipo(tipo);
        o.setImpactoScore(impacto(tipo));
        ocorrencias.save(o);
        ocorrencias.flush();
        recalcular(causador.getId());
        return o;
    }

    @Transactional
    public void recalcular(Long usuarioId) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow();
        LocalDateTime desde = LocalDateTime.now().minusDays(JANELA_DIAS);
        Double media = avaliacoes.mediaVisivelDesde(usuarioId, desde);
        BigDecimal score = BigDecimal.ZERO;
        NivelReputacao nivel = null;
        if (media != null) {
            BigDecimal penalidade = ocorrencias.somarImpactoDesde(usuarioId, desde);
            score = BigDecimal.valueOf(media).subtract(penalidade).max(BigDecimal.ZERO).min(BigDecimal.valueOf(5))
                    .setScale(2, RoundingMode.HALF_UP);
            nivel = nivel(score);
        }
        if (u instanceof Freelancer f) {
            f.setScore(score);
            f.setNivelReputacao(nivel);
        } else if (u instanceof Empresa e) {
            e.setScore(score);
        }
    }

    public static NivelReputacao nivel(BigDecimal score) {
        if (score.compareTo(new BigDecimal("4.50")) >= 0) return NivelReputacao.EXCELENTE;
        if (score.compareTo(new BigDecimal("3.50")) >= 0) return NivelReputacao.BOM;
        if (score.compareTo(new BigDecimal("2.50")) >= 0) return NivelReputacao.REGULAR;
        return NivelReputacao.EM_RISCO;
    }
}
