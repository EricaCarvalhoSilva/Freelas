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

public interface ContaRecebimentoRepository extends JpaRepository<ContaRecebimento, Long> {
    List<ContaRecebimento> findByFreelancerIdAndStatusNotOrderByCriadaEmDesc(Long freelancerId, StatusContaRecebimento status);
    Optional<ContaRecebimento> findByIdAndFreelancerId(Long id, Long freelancerId);
    boolean existsByFreelancerIdAndStatus(Long freelancerId, StatusContaRecebimento status);

    @Modifying
    @Query("""
           update ContaRecebimento c set c.status = br.com.freelas.enums.StatusContaRecebimento.ATIVA
           where c.status = br.com.freelas.enums.StatusContaRecebimento.EM_QUARENTENA and c.liberadaSaqueEm <= :agora
           """)
    int encerrarQuarentenas(@Param("agora") LocalDateTime agora);
}
