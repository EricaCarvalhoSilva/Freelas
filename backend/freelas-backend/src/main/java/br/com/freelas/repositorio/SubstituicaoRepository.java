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

public interface SubstituicaoRepository extends JpaRepository<Substituicao, Long> {

    /** O primeiro que aceitar leva: a linha fica travada durante o aceite. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Substituicao s where s.id = :id")
    Optional<Substituicao> buscarComLock(@Param("id") Long id);

    boolean existsByContratacaoOriginalIdAndStatusIn(Long contratacaoId, Collection<StatusSubstituicao> status);

    /** Convites de reposição abertos que chegaram ao freelancer por notificação. */
    @Query("""
           select s from Substituicao s
           where s.status = br.com.freelas.enums.StatusSubstituicao.AGUARDANDO_SUBSTITUTO
             and exists (select n from Notificacao n where n.usuarioId = :freelancerId
                         and n.tipo = 'SUBSTITUICAO' and n.referenciaId = s.id)
           order by s.solicitadaEm desc
           """)
    List<Substituicao> convitesAbertos(@Param("freelancerId") Long freelancerId);
}
