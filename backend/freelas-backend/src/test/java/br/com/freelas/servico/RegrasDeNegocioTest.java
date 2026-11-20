package br.com.freelas.servico;

import br.com.freelas.entidade.Vaga;
import br.com.freelas.enums.NivelReputacao;
import br.com.freelas.enums.TipoOcorrencia;
import br.com.freelas.enums.TipoValor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Testes de unidade das regras que não dependem do banco. */
class RegrasDeNegocioTest {

    private static Vaga vaga(TipoValor tipo, String valor, LocalDateTime inicio, LocalDateTime fim) {
        Vaga v = new Vaga();
        v.setTipoValor(tipo);
        v.setValor(new BigDecimal(valor));
        v.setInicio(inicio);
        v.setFim(fim);
        return v;
    }

    @Test
    void valorPorHoraMultiplicaPelasHorasDoTurno() {
        LocalDateTime inicio = LocalDateTime.of(2026, 12, 4, 18, 0);
        Vaga v = vaga(TipoValor.HORA, "20.00", inicio, inicio.plusHours(5).plusMinutes(30));
        assertEquals(new BigDecimal("110.00"), VagaService.valorTotal(v));
    }

    @Test
    void valorPorTurnoNaoDependeDasHoras() {
        LocalDateTime inicio = LocalDateTime.of(2026, 12, 4, 18, 0);
        Vaga v = vaga(TipoValor.TURNO, "150", inicio, inicio.plusHours(8));
        assertEquals(new BigDecimal("150.00"), VagaService.valorTotal(v));
    }

    @Test
    void niveisDeReputacaoPelasFaixasDoScore() {
        assertEquals(NivelReputacao.EXCELENTE, ReputacaoService.nivel(new BigDecimal("4.50")));
        assertEquals(NivelReputacao.BOM, ReputacaoService.nivel(new BigDecimal("4.49")));
        assertEquals(NivelReputacao.BOM, ReputacaoService.nivel(new BigDecimal("3.50")));
        assertEquals(NivelReputacao.REGULAR, ReputacaoService.nivel(new BigDecimal("2.50")));
        assertEquals(NivelReputacao.EM_RISCO, ReputacaoService.nivel(new BigDecimal("2.49")));
    }

    @Test
    void noShowPesaMaisQueCancelamentoPrevio() {
        assertTrue(ReputacaoService.impacto(TipoOcorrencia.NO_SHOW)
                .compareTo(ReputacaoService.impacto(TipoOcorrencia.CANCELAMENTO_TARDIO)) > 0);
        assertTrue(ReputacaoService.impacto(TipoOcorrencia.CANCELAMENTO_TARDIO)
                .compareTo(ReputacaoService.impacto(TipoOcorrencia.CANCELAMENTO_PREVIO)) > 0);
    }

    @Test
    void diasUteisPulamOFimDeSemana() {
        LocalDateTime sexta = LocalDateTime.of(2026, 11, 20, 10, 0);
        // 5 dias úteis a partir de sexta = sexta da semana seguinte
        assertEquals(LocalDateTime.of(2026, 11, 27, 10, 0), AvaliacaoService.somarDiasUteis(sexta, 5));
        // 1 dia útil a partir de sexta = segunda
        assertEquals(LocalDateTime.of(2026, 11, 23, 10, 0), AvaliacaoService.somarDiasUteis(sexta, 1));
    }

    @Test
    void comentarioOfensivoEhBarradoMesmoComAcentoOuMaiuscula() {
        assertTrue(AvaliacaoService.contemPalavraProibida("Que OTÁRIO, chegou atrasado"));
        assertFalse(AvaliacaoService.contemPalavraProibida("Ótimo atendimento, muito pontual"));
    }
}
