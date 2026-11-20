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

public interface TokenRenovacaoRepository extends JpaRepository<TokenRenovacao, Long> {
    Optional<TokenRenovacao> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update TokenRenovacao t set t.revogadoEm = :agora where t.usuarioId = :usuarioId and t.revogadoEm is null")
    int revogarTodos(@Param("usuarioId") Long usuarioId, @Param("agora") LocalDateTime agora);
}
