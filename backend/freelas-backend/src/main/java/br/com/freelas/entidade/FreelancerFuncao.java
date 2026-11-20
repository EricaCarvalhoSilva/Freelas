package br.com.freelas.entidade;

import br.com.freelas.enums.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Classe intermediária do N:N Freelancer x Funcao, com os anos de experiência. */
@Entity
@Table(name = "freelancer_funcao")
@Getter @Setter @NoArgsConstructor
public class FreelancerFuncao {
    @EmbeddedId
    private FreelancerFuncaoId id = new FreelancerFuncaoId();

    @MapsId("freelancerId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "freelancer_id")
    private Freelancer freelancer;

    @MapsId("funcaoId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "funcao_id")
    private Funcao funcao;

    @Column(name = "anos_experiencia")
    private Short anosExperiencia;
}
