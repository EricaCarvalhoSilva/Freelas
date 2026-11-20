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
@Table(name = "empresa")
@DiscriminatorValue("EMPRESA")
@PrimaryKeyJoinColumn(name = "usuario_id")
@Getter @Setter @NoArgsConstructor
public class Empresa extends Usuario {
    @Column(name = "razao_social", nullable = false, length = 150)
    private String razaoSocial;

    @Column(name = "nome_fantasia", nullable = false, length = 150)
    private String nomeFantasia;

    @JdbcTypeCode(SqlTypes.CHAR)

    @Column(nullable = false, unique = true, length = 14)
    private String cnpj;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private CategoriaEmpresa categoria;

    @Column(name = "responsavel_nome", length = 120)
    private String responsavelNome;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "endereco_id", nullable = false)
    private Endereco endereco;

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal score = BigDecimal.ZERO;

    @Override
    public TipoUsuario getTipo() { return TipoUsuario.EMPRESA; }

    @Override
    public String getNomeExibicao() { return nomeFantasia; }
}
