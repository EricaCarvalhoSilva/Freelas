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

/** "Contestar avaliação" em até 5 dias úteis. */
@Entity
@Table(name = "contestacao")
@Getter @Setter @NoArgsConstructor
public class Contestacao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "avaliacao_id", nullable = false, unique = true)
    private Avaliacao avaliacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitante_id", nullable = false)
    private Usuario solicitante;

    @Column(nullable = false, columnDefinition = "text")
    private String justificativa;

    @Column(name = "evidencia_url")
    private String evidenciaUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatusContestacao status = StatusContestacao.ABERTA;

    @Column(name = "aberta_em", nullable = false)
    private LocalDateTime abertaEm = LocalDateTime.now();

    @Column(name = "resolvida_em")
    private LocalDateTime resolvidaEm;
}
