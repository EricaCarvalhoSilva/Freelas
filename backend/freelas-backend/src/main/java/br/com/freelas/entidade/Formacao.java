package br.com.freelas.entidade;

import br.com.freelas.enums.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Formações e conhecimentos do freelancer (RF6). Composição com Freelancer. */
@Entity
@Table(name = "formacao")
@Getter @Setter @NoArgsConstructor
public class Formacao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "freelancer_id", nullable = false)
    private Freelancer freelancer;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(length = 120)
    private String instituicao;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TipoFormacao tipo;

    @Column(name = "ano_conclusao")
    private Short anoConclusao;
}
