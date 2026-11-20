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

public interface VagaFavoritaRepository extends JpaRepository<VagaFavorita, VagaFavoritaId> {
    @Query(value = "select vf from VagaFavorita vf join fetch vf.vaga where vf.freelancer.id = :freelancerId",
           countQuery = "select count(vf) from VagaFavorita vf where vf.freelancer.id = :freelancerId")
    Page<VagaFavorita> listarDoFreelancer(@Param("freelancerId") Long freelancerId, Pageable pageable);
}
