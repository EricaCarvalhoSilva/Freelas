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

/** Pedido de substituição (UC13). O primeiro freelancer que aceitar fica com a vaga. */
@Entity
@Table(name = "substituicao")
@Getter @Setter @NoArgsConstructor
public class Substituicao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contratacao_original_id", nullable = false)
    private Contratacao contratacaoOriginal;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nova_contratacao_id", unique = true)
    private Contratacao novaContratacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private MotivoSubstituicao motivo;

    @Column(name = "raio_km", nullable = false, precision = 5, scale = 1)
    private BigDecimal raioKm;

    @Column(name = "solicitada_em", nullable = false)
    private LocalDateTime solicitadaEm = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private StatusSubstituicao status = StatusSubstituicao.AGUARDANDO_SUBSTITUTO;
}
