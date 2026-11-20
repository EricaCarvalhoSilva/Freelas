package br.com.freelas.entidade;

import br.com.freelas.enums.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Perfil Administrador (RF21): media disputas e contestações. Tabela criada na V5. */
@Entity
@Table(name = "administrador")
@DiscriminatorValue("ADMINISTRADOR")
@PrimaryKeyJoinColumn(name = "usuario_id")
@Getter @Setter @NoArgsConstructor
public class Administrador extends Usuario {
    @Column(nullable = false, length = 120)
    private String nome;

    @Override
    public TipoUsuario getTipo() { return TipoUsuario.ADMINISTRADOR; }

    @Override
    public String getNomeExibicao() { return nome; }
}
