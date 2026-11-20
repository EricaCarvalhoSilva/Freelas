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

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {
    Page<Notificacao> findByUsuarioId(Long usuarioId, Pageable pageable);
    Page<Notificacao> findByUsuarioIdAndLida(Long usuarioId, Boolean lida, Pageable pageable);
    Optional<Notificacao> findByIdAndUsuarioId(Long id, Long usuarioId);
    long countByTipoAndReferenciaId(String tipo, Long referenciaId);
    boolean existsByUsuarioIdAndTipoAndReferenciaId(Long usuarioId, String tipo, Long referenciaId);

    /**
     * Aviso de nova vaga em lote: um único INSERT ... SELECT grava a notificação para
     * todos os freelancers ativos que exercem a função (técnica de escrita em lote).
     */
    @Modifying
    @Query(value = """
           insert into notificacao (usuario_id, tipo, mensagem, referencia_id, criada_em, lida)
           select ff.freelancer_id, 'NOVA_VAGA', :mensagem, :vagaId, now(), false
           from freelancer_funcao ff
           join usuario u on u.id = ff.freelancer_id
           where ff.funcao_id = :funcaoId and u.status = 'ATIVO'
           """, nativeQuery = true)
    int notificarNovaVagaEmLote(@Param("funcaoId") Integer funcaoId, @Param("vagaId") Long vagaId,
                                @Param("mensagem") String mensagem);
}
