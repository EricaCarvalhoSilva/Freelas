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

public interface AssinaturaRepository extends JpaRepository<Assinatura, Long> {
    /** Assinatura que ainda dá acesso: ATIVA, ou CANCELADA dentro do período pago. */
    @Query("""
           select a from Assinatura a
           where a.usuario.id = :usuarioId and a.fim > :agora
             and a.status in (br.com.freelas.enums.StatusAssinatura.ATIVA, br.com.freelas.enums.StatusAssinatura.CANCELADA)
           order by a.fim desc
           """)
    List<Assinatura> vigentes(@Param("usuarioId") Long usuarioId, @Param("agora") LocalDateTime agora);

    List<Assinatura> findByUsuarioIdOrderByInicioDesc(Long usuarioId);

    Optional<Assinatura> findByIdTransacaoGateway(String idTransacaoGateway);

    @Modifying
    @Query("""
           update Assinatura a set a.status = br.com.freelas.enums.StatusAssinatura.EXPIRADA
           where a.fim <= :agora
             and a.status in (br.com.freelas.enums.StatusAssinatura.ATIVA, br.com.freelas.enums.StatusAssinatura.CANCELADA)
           """)
    int expirar(@Param("agora") LocalDateTime agora);
}
