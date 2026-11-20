package br.com.freelas.enums;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Opções de destaque de vaga (UC15, RF19) e de Boost de perfil.
 * Os documentos não têm tabela para isso, então os preços ficam fixos aqui.
 */
public enum OpcaoDestaque {
    VAGA_24H(TipoImpulsionamento.DESTAQUE_VAGA, Duration.ofHours(24), new BigDecimal("9.90")),
    VAGA_7D(TipoImpulsionamento.DESTAQUE_VAGA, Duration.ofDays(7), new BigDecimal("24.90")),
    BOOST_24H(TipoImpulsionamento.BOOST_PERFIL, Duration.ofHours(24), new BigDecimal("4.90"));

    private final TipoImpulsionamento tipo;
    private final Duration duracao;
    private final BigDecimal preco;

    OpcaoDestaque(TipoImpulsionamento tipo, Duration duracao, BigDecimal preco) {
        this.tipo = tipo;
        this.duracao = duracao;
        this.preco = preco;
    }

    public TipoImpulsionamento getTipo() { return tipo; }
    public Duration getDuracao() { return duracao; }
    public BigDecimal getPreco() { return preco; }
}
