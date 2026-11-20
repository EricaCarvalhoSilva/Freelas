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

/** Tarefa assíncrona da fila no PostgreSQL (V2). */
@Entity
@Table(name = "fila_tarefa")
@Getter @Setter @NoArgsConstructor
public class FilaTarefa {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TipoTarefa tipo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload = "{}";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private StatusTarefa status = StatusTarefa.PENDENTE;

    @Column(nullable = false)
    private Short tentativas = 0;

    @Column(name = "max_tentativas", nullable = false)
    private Short maxTentativas = 5;

    @Column(name = "executar_em", nullable = false)
    private LocalDateTime executarEm = LocalDateTime.now();

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm = LocalDateTime.now();

    @Column(name = "atualizada_em", nullable = false)
    private LocalDateTime atualizadaEm = LocalDateTime.now();

    @Column(name = "ultimo_erro", length = 500)
    private String ultimoErro;
}
