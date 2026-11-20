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

/** Aparelho que recebe notificação push (V3). */
@Entity
@Table(name = "dispositivo_push")
@Getter @Setter @NoArgsConstructor
public class DispositivoPush {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "token_push", nullable = false, unique = true)
    private String tokenPush;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Plataforma plataforma;

    @Column(name = "registrado_em", nullable = false)
    private LocalDateTime registradoEm = LocalDateTime.now();

    @Column(nullable = false)
    private Boolean ativo = true;
}
