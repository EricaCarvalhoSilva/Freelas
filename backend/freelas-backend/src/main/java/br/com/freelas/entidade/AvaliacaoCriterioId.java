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
public class AvaliacaoCriterioId implements Serializable {
    @Column(name = "avaliacao_id")
    private Long avaliacaoId;

    @Column(name = "criterio", length = 30)
    private String criterio;
}
