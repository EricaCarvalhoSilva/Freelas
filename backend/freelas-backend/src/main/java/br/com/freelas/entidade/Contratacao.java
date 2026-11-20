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

/** O turno: uma candidatura aceita vira no máximo uma contratação (KAN-70 RN08 a RN17). */
@Entity
@Table(name = "contratacao")
@Getter @Setter @NoArgsConstructor
public class Contratacao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidatura_id", nullable = false, unique = true)
    private Candidatura candidatura;

    @Column(name = "valor_acordado", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorAcordado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 18)
    private StatusContratacao status = StatusContratacao.AGUARDANDO_ACEITE;

    @Column(name = "confirmada_em")
    private LocalDateTime confirmadaEm;

    @Column(name = "checkin_em")
    private LocalDateTime checkinEm;

    @Column(name = "checkout_em")
    private LocalDateTime checkoutEm;

    @Column(name = "encerrada_em")
    private LocalDateTime encerradaEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelada_por")
    private Usuario canceladaPor;

    @Column(name = "motivo_encerramento")
    private String motivoEncerramento;

    public Vaga getVaga() { return candidatura.getVaga(); }
    public Freelancer getFreelancer() { return candidatura.getFreelancer(); }
    public Empresa getEmpresa() { return candidatura.getVaga().getEmpresa(); }
}
