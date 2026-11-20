package br.com.freelas.repositorio;

import br.com.freelas.entidade.*;
import br.com.freelas.enums.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AvaliacaoRepository extends JpaRepository<Avaliacao, Long> {
    boolean existsByContratacaoIdAndDirecao(Long contratacaoId, DirecaoAvaliacao direcao);

    Optional<Avaliacao> findByContratacaoIdAndDirecao(Long contratacaoId, DirecaoAvaliacao direcao);

    List<Avaliacao> findByContratacaoId(Long contratacaoId);

    /** Média das notas visíveis recebidas nos últimos 90 dias (Definição de regras de score). */
    @Query("""
           select avg(a.nota) from Avaliacao a
           where a.avaliado.id = :usuarioId and a.visivel = true
             and a.status <> br.com.freelas.enums.StatusAvaliacao.REMOVIDA
             and a.enviadaEm >= :desde
           """)
    Double mediaVisivelDesde(@Param("usuarioId") Long usuarioId, @Param("desde") LocalDateTime desde);

    @Query(value = """
           select a from Avaliacao a
           where a.avaliado.id = :usuarioId and a.visivel = true
             and a.status <> br.com.freelas.enums.StatusAvaliacao.REMOVIDA
           """,
           countQuery = """
           select count(a) from Avaliacao a
           where a.avaliado.id = :usuarioId and a.visivel = true
             and a.status <> br.com.freelas.enums.StatusAvaliacao.REMOVIDA
           """)
    Page<Avaliacao> listarVisiveisRecebidas(@Param("usuarioId") Long usuarioId, Pageable pageable);

    /** Avaliações cegas cuja janela de 72 h acabou. */
    List<Avaliacao> findByVisivelFalseAndEnviadaEmBefore(LocalDateTime limite);
}
