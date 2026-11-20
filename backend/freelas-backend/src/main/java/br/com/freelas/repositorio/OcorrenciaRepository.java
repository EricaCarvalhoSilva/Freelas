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

public interface OcorrenciaRepository extends JpaRepository<Ocorrencia, Long> {
    @Query("select coalesce(sum(o.impactoScore), 0) from Ocorrencia o where o.usuario.id = :usuarioId and o.registradaEm >= :desde")
    BigDecimal somarImpactoDesde(@Param("usuarioId") Long usuarioId, @Param("desde") LocalDateTime desde);

    boolean existsByUsuarioIdAndRegistradaEmAfter(Long usuarioId, LocalDateTime desde);

    boolean existsByUsuarioIdAndTipoAndRegistradaEmAfter(Long usuarioId, TipoOcorrencia tipo, LocalDateTime desde);

    List<Ocorrencia> findByUsuarioIdAndRegistradaEmAfterOrderByRegistradaEmDesc(Long usuarioId, LocalDateTime desde);
}
