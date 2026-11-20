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

/** Nota por critério (pontualidade, alimentação, segurança...). Composição com Avaliacao. */
@Entity
@Table(name = "avaliacao_criterio")
@Getter @Setter @NoArgsConstructor
public class AvaliacaoCriterio {
    @EmbeddedId
    private AvaliacaoCriterioId id = new AvaliacaoCriterioId();

    @MapsId("avaliacaoId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "avaliacao_id")
    private Avaliacao avaliacao;

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal nota;

    public String getCriterio() { return id.getCriterio(); }
}
