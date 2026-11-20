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

/** Pagamento do turno em custódia, com a divisão (taxa da plataforma e valor líquido). */
@Entity
@Table(name = "pagamento")
@Getter @Setter @NoArgsConstructor
public class Pagamento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contratacao_id", nullable = false, unique = true)
    private Contratacao contratacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meio_pagamento_id", nullable = false)
    private MeioPagamentoEmpresa meioPagamento;

    @Column(name = "valor_bruto", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorBruto;

    @Column(name = "taxa_plataforma", nullable = false, precision = 10, scale = 2)
    private BigDecimal taxaPlataforma;

    @Column(name = "valor_liquido", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorLiquido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 22)
    private StatusPagamento status = StatusPagamento.PAGO_CUSTODIA;

    @Column(name = "id_transacao_gateway", nullable = false, unique = true, length = 80)
    private String idTransacaoGateway;

    @Column(name = "chave_idempotencia", nullable = false, unique = true, length = 80)
    private String chaveIdempotencia;

    @Column(name = "pago_em", nullable = false)
    private LocalDateTime pagoEm = LocalDateTime.now();

    @Column(name = "liberado_em")
    private LocalDateTime liberadoEm;

    @Column(name = "motivo_disputa", length = 500)
    private String motivoDisputa;
}
