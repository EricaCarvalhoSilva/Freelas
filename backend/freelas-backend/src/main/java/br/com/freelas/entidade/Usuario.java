package br.com.freelas.entidade;

import br.com.freelas.enums.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Superclasse abstrata (diagrama de classes): dados de login comuns.
 * Herança JOINED: a tabela usuario guarda o "tipo" e cada subclasse tem a sua tabela.
 */
@Entity
@Table(name = "usuario")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "tipo", discriminatorType = DiscriminatorType.STRING, length = 13)
@Getter @Setter @NoArgsConstructor
public abstract class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Column(nullable = false, length = 20)
    private String telefone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private StatusUsuario status = StatusUsuario.PENDENTE;

    @Column(name = "email_confirmado", nullable = false)
    private Boolean emailConfirmado = false;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    /** Perfil do usuário, igual ao valor gravado na coluna tipo. */
    public abstract TipoUsuario getTipo();

    /** Nome exibido (nome do freelancer ou nome fantasia da empresa). */
    public abstract String getNomeExibicao();
}
