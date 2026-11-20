package br.com.freelas.entidade;

import br.com.freelas.enums.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** N:N Freelancer x Vaga "preferida" (RF18). */
@Entity
@Table(name = "vaga_favorita")
@Getter @Setter @NoArgsConstructor
public class VagaFavorita {
    @EmbeddedId
    private VagaFavoritaId id = new VagaFavoritaId();

    @MapsId("freelancerId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "freelancer_id")
    private Freelancer freelancer;

    @MapsId("vagaId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vaga_id")
    private Vaga vaga;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm = LocalDateTime.now();
}
