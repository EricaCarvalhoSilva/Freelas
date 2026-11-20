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

public interface CandidaturaRepository extends JpaRepository<Candidatura, Long> {
    boolean existsByVagaIdAndFreelancerId(Long vagaId, Long freelancerId);

    Optional<Candidatura> findByVagaIdAndFreelancerId(Long vagaId, Long freelancerId);

    Page<Candidatura> findByFreelancerId(Long freelancerId, Pageable pageable);
    Page<Candidatura> findByFreelancerIdAndStatus(Long freelancerId, StatusCandidatura status, Pageable pageable);

    /** Candidatos da vaga, sem freelancers bloqueados, suspensos ou excluídos (KAN-70 RN04). */
    @Query("""
           select c from Candidatura c join fetch c.freelancer f
           where c.vaga.id = :vagaId
             and f.status not in (br.com.freelas.enums.StatusUsuario.BLOQUEADO,
                                  br.com.freelas.enums.StatusUsuario.SUSPENSO,
                                  br.com.freelas.enums.StatusUsuario.EXCLUIDO)
           """)
    List<Candidatura> listarVisiveisDaVaga(@Param("vagaId") Long vagaId);

    /** A empresa só vê o perfil de quem se candidatou a uma vaga dela. */
    boolean existsByFreelancerIdAndVagaEmpresaId(Long freelancerId, Long empresaId);

    @Modifying
    @Query("""
           update Candidatura c set c.status = br.com.freelas.enums.StatusCandidatura.RECUSADA
           where c.vaga.id = :vagaId and c.status = br.com.freelas.enums.StatusCandidatura.ENVIADA
           """)
    int recusarPendentesDaVaga(@Param("vagaId") Long vagaId);
}
