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

public interface FreelancerFuncaoRepository extends JpaRepository<FreelancerFuncao, FreelancerFuncaoId> {
    @Query("select ff from FreelancerFuncao ff join fetch ff.funcao where ff.freelancer.id = :freelancerId order by ff.funcao.nome")
    List<FreelancerFuncao> listarDoFreelancer(@Param("freelancerId") Long freelancerId);

    @Modifying
    @Query("delete from FreelancerFuncao ff where ff.freelancer.id = :freelancerId")
    void apagarDoFreelancer(@Param("freelancerId") Long freelancerId);

    boolean existsByFreelancerIdAndFuncaoId(Long freelancerId, Integer funcaoId);
}
