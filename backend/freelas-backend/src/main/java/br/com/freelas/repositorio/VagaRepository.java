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

public interface VagaRepository extends JpaRepository<Vaga, Long>, JpaSpecificationExecutor<Vaga> {

    /** SELECT ... FOR UPDATE: evita contratar mais gente do que a vaga comporta. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vaga v where v.id = :id")
    Optional<Vaga> buscarComLock(@Param("id") Long id);

    Page<Vaga> findByEmpresaId(Long empresaId, Pageable pageable);
    Page<Vaga> findByEmpresaIdAndStatus(Long empresaId, StatusVaga status, Pageable pageable);

    /** Tarefa agendada: encerra vagas publicadas cujo prazo de disponibilidade ou início já passou (KAN-69 RN12). */
    @Modifying
    @Query("""
           update Vaga v set v.status = br.com.freelas.enums.StatusVaga.ENCERRADA_PRAZO
           where v.status = br.com.freelas.enums.StatusVaga.PUBLICADA
             and (v.inicio < :agora or (v.disponivelAte is not null and v.disponivelAte < :agora))
           """)
    int encerrarPorPrazo(@Param("agora") LocalDateTime agora);

    /** Tarefa agendada: vaga preenchida cujo turno terminou vira CONCLUIDA. */
    @Modifying
    @Query("""
           update Vaga v set v.status = br.com.freelas.enums.StatusVaga.CONCLUIDA
           where v.status in (br.com.freelas.enums.StatusVaga.PREENCHIDA, br.com.freelas.enums.StatusVaga.ENCERRADA_PRAZO)
             and v.fim < :limite
           """)
    int concluirTerminadas(@Param("limite") LocalDateTime limite);
}
