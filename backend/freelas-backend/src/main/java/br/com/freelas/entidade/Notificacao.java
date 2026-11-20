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

@Entity
@Table(name = "notificacao")
@Getter @Setter @NoArgsConstructor
public class Notificacao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    /** NOVA_VAGA, CANDIDATURA, CONTRATACAO, SUBSTITUICAO, PAGAMENTO... */
    @Column(nullable = false, length = 30)
    private String tipo;

    @Column(nullable = false)
    private String mensagem;

    @Column(name = "referencia_id")
    private Long referenciaId;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm = LocalDateTime.now();

    @Column(nullable = false)
    private Boolean lida = false;
}
