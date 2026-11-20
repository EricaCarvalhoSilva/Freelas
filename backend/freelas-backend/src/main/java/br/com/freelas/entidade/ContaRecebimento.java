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

/** Chave Pix ou conta bancária do freelancer (RF9, RF12: quarentena de 24 h). Dados criptografados. */
@Entity
@Table(name = "conta_recebimento")
@Getter @Setter @NoArgsConstructor
public class ContaRecebimento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "freelancer_id", nullable = false)
    private Freelancer freelancer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 14)
    private TipoContaRecebimento tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_chave_pix", length = 10)
    private TipoChavePix tipoChavePix;

    @Column(name = "chave_pix_cript")
    private String chavePixCript;

    @Column(length = 60)
    private String banco;

    @Column(name = "agencia_cript")
    private String agenciaCript;

    @Column(name = "conta_cript")
    private String contaCript;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusContaRecebimento status = StatusContaRecebimento.EM_QUARENTENA;

    @Column(name = "liberada_saque_em", nullable = false)
    private LocalDateTime liberadaSaqueEm;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm = LocalDateTime.now();
}
