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

public interface FormacaoRepository extends JpaRepository<Formacao, Long> {
    List<Formacao> findByFreelancerIdOrderByAnoConclusaoDesc(Long freelancerId);
    Optional<Formacao> findByIdAndFreelancerId(Long id, Long freelancerId);
}
