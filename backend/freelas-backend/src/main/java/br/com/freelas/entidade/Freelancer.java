package br.com.freelas.entidade;

import br.com.freelas.enums.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "freelancer")
@DiscriminatorValue("FREELANCER")
@PrimaryKeyJoinColumn(name = "usuario_id")
@Getter @Setter @NoArgsConstructor
public class Freelancer extends Usuario {
    @Column(nullable = false, length = 120)
    private String nome;

    @JdbcTypeCode(SqlTypes.CHAR)

    @Column(nullable = false, unique = true, length = 11)
    private String cpf;

    @Column(name = "data_nascimento", nullable = false)
    private LocalDate dataNascimento;

    @Column(name = "foto_url")
    private String fotoUrl;

    @Column(columnDefinition = "text")
    private String apresentacao;

    @Column(name = "curriculo_url")
    private String curriculoUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endereco_id")
    private Endereco endereco;

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal score = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_reputacao", length = 10)
    private NivelReputacao nivelReputacao;

    @Column(nullable = false)
    private Boolean verificado = false;

    /** No-show: fica 7 dias sem poder se candidatar. */
    @Column(name = "suspenso_ate")
    private LocalDateTime suspensoAte;

    @Override
    public TipoUsuario getTipo() { return TipoUsuario.FREELANCER; }

    @Override
    public String getNomeExibicao() { return nome; }
}
