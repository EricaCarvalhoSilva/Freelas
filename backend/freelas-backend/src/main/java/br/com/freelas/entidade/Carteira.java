package br.com.freelas.entidade;

import br.com.freelas.enums.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Carteira digital do freelancer (1:1). Alterada sempre com FOR UPDATE. */
@Entity
@Table(name = "carteira")
@Getter @Setter @NoArgsConstructor
public class Carteira {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "freelancer_id", nullable = false, unique = true)
    private Freelancer freelancer;

    @Column(name = "saldo_disponivel", nullable = false, precision = 12, scale = 2)
    private BigDecimal saldoDisponivel = BigDecimal.ZERO;

    @Column(name = "saldo_pendente", nullable = false, precision = 12, scale = 2)
    private BigDecimal saldoPendente = BigDecimal.ZERO;

    @Column(name = "saldo_bloqueado", nullable = false, precision = 12, scale = 2)
    private BigDecimal saldoBloqueado = BigDecimal.ZERO;
}
