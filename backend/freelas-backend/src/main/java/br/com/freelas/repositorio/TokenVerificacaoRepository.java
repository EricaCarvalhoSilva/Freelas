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

public interface TokenVerificacaoRepository extends JpaRepository<TokenVerificacao, Long> {
    Optional<TokenVerificacao> findFirstByUsuarioIdAndFinalidadeAndUsadoEmIsNullOrderByCriadoEmDesc(
            Long usuarioId, FinalidadeToken finalidade);

    @Modifying
    @Query("""
           update TokenVerificacao t set t.usadoEm = :agora
           where t.usuarioId = :usuarioId and t.finalidade = :finalidade and t.usadoEm is null
           """)
    int invalidarAnteriores(@Param("usuarioId") Long usuarioId, @Param("finalidade") FinalidadeToken finalidade,
                            @Param("agora") LocalDateTime agora);
}
