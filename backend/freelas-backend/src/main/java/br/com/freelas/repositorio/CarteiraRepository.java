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

public interface CarteiraRepository extends JpaRepository<Carteira, Long> {
    Optional<Carteira> findByFreelancerId(Long freelancerId);

    /** SELECT ... FOR UPDATE na carteira antes de mexer no saldo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Carteira c where c.freelancer.id = :freelancerId")
    Optional<Carteira> buscarComLockPorFreelancer(@Param("freelancerId") Long freelancerId);
}
