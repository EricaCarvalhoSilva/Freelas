package br.com.freelas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/** Lê o bloco "freelas:" do application.yml. */
@ConfigurationProperties(prefix = "freelas")
public record PropriedadesFreelas(Seguranca seguranca, Regras regras, Admin admin, Worker worker) {

    public record Seguranca(
            String jwtSegredo,
            int tokenAcessoMinutos,
            int tokenRenovacaoInatividadeMinutos,
            int tokenRenovacaoMaximoDias,
            int codigoEmailMinutos,
            String chaveCriptografia,
            String webhookSegredo) {}

    public record Regras(
            BigDecimal taxaPlataformaPercentual,
            BigDecimal valorMinimoHora,
            BigDecimal valorMinimoTurno,
            BigDecimal valorMinimoDiaria,
            int idadeMinima,
            int janelaConfirmacaoPagamentoHoras,
            int janelaAvaliacaoHoras,
            int contestacaoDiasUteis,
            int quarentenaContaRecebimentoHoras,
            int cancelamentoAntecedenciaHoras,
            int suspensaoNoShowDias,
            int acessoAntecipadoPremiumMinutos,
            BigDecimal raioPadraoSubstituicaoKm,
            int checkinAntecedenciaMinutos) {}

    public record Admin(String email, String senha) {}

    public record Worker(long intervaloMs, int lote) {}
}
