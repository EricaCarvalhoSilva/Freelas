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

/** Cartão ou Pix da empresa tokenizado no gateway (RNF17: nunca o número do cartão). */
@Entity
@Table(name = "meio_pagamento_empresa")
@Getter @Setter @NoArgsConstructor
public class MeioPagamentoEmpresa {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoMeioPagamento tipo;

    @Column(name = "token_gateway", nullable = false, length = 120)
    private String tokenGateway;

    @JdbcTypeCode(SqlTypes.CHAR)

    @Column(name = "final_cartao", length = 4)
    private String finalCartao;

    @Column(nullable = false)
    private Boolean ativo = true;
}
