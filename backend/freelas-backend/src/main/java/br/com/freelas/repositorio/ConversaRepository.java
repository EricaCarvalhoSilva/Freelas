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

public interface ConversaRepository extends JpaRepository<Conversa, Long> {
    Optional<Conversa> findByCandidaturaId(Long candidaturaId);

    @Query("select c from Conversa c join c.candidatura ca join ca.vaga v where v.empresa.id = :empresaId order by c.criadaEm desc")
    Page<Conversa> listarDaEmpresa(@Param("empresaId") Long empresaId, Pageable pageable);

    @Query("select c from Conversa c join c.candidatura ca where ca.freelancer.id = :freelancerId order by c.criadaEm desc")
    Page<Conversa> listarDoFreelancer(@Param("freelancerId") Long freelancerId, Pageable pageable);
}
