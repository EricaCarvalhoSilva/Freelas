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

/** Avaliação mútua: no máximo uma por direção em cada contratação concluída. */
@Entity
@Table(name = "avaliacao")
@Getter @Setter @NoArgsConstructor
public class Avaliacao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contratacao_id", nullable = false)
    private Contratacao contratacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "avaliador_id", nullable = false)
    private Usuario avaliador;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "avaliado_id", nullable = false)
    private Usuario avaliado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 17)
    private DirecaoAvaliacao direcao;

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal nota;

    @Column(length = 500)
    private String comentario;

    @Column(name = "enviada_em", nullable = false)
    private LocalDateTime enviadaEm = LocalDateTime.now();

    /** Avaliação cega: só aparece quando os dois lados avaliam ou quando as 72 h acabam. */
    @Column(nullable = false)
    private Boolean visivel = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatusAvaliacao status = StatusAvaliacao.PUBLICADA;

    @OneToMany(mappedBy = "avaliacao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AvaliacaoCriterio> criterios = new ArrayList<>();
}
