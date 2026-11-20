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

public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {
    Optional<Pagamento> findByContratacaoId(Long contratacaoId);
    Optional<Pagamento> findByIdTransacaoGateway(String idTransacaoGateway);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pagamento p where p.id = :id")
    Optional<Pagamento> buscarComLock(@Param("id") Long id);

    @Query("""
           select p from Pagamento p join p.contratacao c join c.candidatura ca join ca.vaga v
           where v.empresa.id = :empresaId
             and (:status is null or p.status = :status)
             and p.pagoEm >= :de and p.pagoEm < :ate
           """)
    Page<Pagamento> listarDaEmpresa(@Param("empresaId") Long empresaId, @Param("status") StatusPagamento status,
                                    @Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate, Pageable pageable);

    Page<Pagamento> findByStatus(StatusPagamento status, Pageable pageable);
}
