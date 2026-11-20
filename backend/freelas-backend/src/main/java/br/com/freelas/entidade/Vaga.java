package br.com.freelas.entidade;

import br.com.freelas.enums.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "vaga")
@Getter @Setter @NoArgsConstructor
public class Vaga {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "funcao_id", nullable = false)
    private Funcao funcao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "endereco_id", nullable = false)
    private Endereco endereco;

    @Column(columnDefinition = "text")
    private String descricao;

    @Column(name = "quantidade_profissionais", nullable = false)
    private Short quantidadeProfissionais;

    @Column(nullable = false)
    private LocalDateTime inicio;

    @Column(nullable = false)
    private LocalDateTime fim;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_valor", nullable = false, length = 7)
    private TipoValor tipoValor;

    @Column(name = "oferece_alimentacao", nullable = false)
    private Boolean ofereceAlimentacao;

    @Column(name = "tipo_alimentacao", length = 40)
    private String tipoAlimentacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", nullable = false, length = 10)
    private FormaPagamento formaPagamento;

    /** Vaga relâmpago = vaga de substituição. */
    @Column(nullable = false)
    private Boolean relampago = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private StatusVaga status = StatusVaga.RASCUNHO;

    @Column(name = "publicada_em")
    private LocalDateTime publicadaEm;

    @Column(name = "disponivel_ate")
    private LocalDateTime disponivelAte;
}
