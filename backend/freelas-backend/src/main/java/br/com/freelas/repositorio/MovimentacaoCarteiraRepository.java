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

public interface MovimentacaoCarteiraRepository extends JpaRepository<MovimentacaoCarteira, Long> {
    @Query("""
           select m from MovimentacaoCarteira m
           where m.carteira.id = :carteiraId
             and (:tipo is null or m.tipo = :tipo)
             and m.criadaEm >= :de and m.criadaEm < :ate
           """)
    Page<MovimentacaoCarteira> extrato(@Param("carteiraId") Long carteiraId, @Param("tipo") TipoMovimentacao tipo,
                                       @Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate, Pageable pageable);

    Optional<MovimentacaoCarteira> findByChaveIdempotencia(String chave);

    Optional<MovimentacaoCarteira> findByIdAndCarteiraIdAndTipo(Long id, Long carteiraId, TipoMovimentacao tipo);
}
