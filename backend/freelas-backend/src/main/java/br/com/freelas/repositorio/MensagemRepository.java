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

public interface MensagemRepository extends JpaRepository<Mensagem, Long> {
    /** Da mais nova para a mais antiga; ?antesDe= pagina pelo histórico (sem filtro, o serviço passa uma data no futuro). */
    @Query("""
           select m from Mensagem m
           where m.conversa.id = :conversaId and m.enviadaEm < :antesDe
           order by m.enviadaEm desc, m.id desc
           """)
    List<Mensagem> listar(@Param("conversaId") Long conversaId, @Param("antesDe") LocalDateTime antesDe, Pageable pageable);

    Optional<Mensagem> findFirstByConversaIdOrderByEnviadaEmDescIdDesc(Long conversaId);

    long countByConversaIdAndRemetenteIdNotAndLidaEmIsNull(Long conversaId, Long usuarioId);

    Optional<Mensagem> findByConversaIdAndIdCliente(Long conversaId, String idCliente);

    @Modifying
    @Query("""
           update Mensagem m set m.lidaEm = :agora
           where m.conversa.id = :conversaId and m.remetente.id <> :leitorId and m.lidaEm is null
           """)
    int marcarComoLidas(@Param("conversaId") Long conversaId, @Param("leitorId") Long leitorId,
                        @Param("agora") LocalDateTime agora);
}
