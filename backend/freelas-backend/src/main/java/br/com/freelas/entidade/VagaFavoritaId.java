package br.com.freelas.entidade;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class VagaFavoritaId implements Serializable {
    @Column(name = "freelancer_id")
    private Long freelancerId;

    @Column(name = "vaga_id")
    private Long vagaId;
}
