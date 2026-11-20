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

public interface ImpulsionamentoRepository extends JpaRepository<Impulsionamento, Long> {
    List<Impulsionamento> findByVagaIdOrderByInicioDesc(Long vagaId);
    List<Impulsionamento> findByFreelancerIdOrderByInicioDesc(Long freelancerId);
    Optional<Impulsionamento> findByIdTransacaoGateway(String idTransacaoGateway);
    boolean existsByVagaIdAndFimAfter(Long vagaId, LocalDateTime agora);
    boolean existsByFreelancerIdAndFimAfter(Long freelancerId, LocalDateTime agora);
}
