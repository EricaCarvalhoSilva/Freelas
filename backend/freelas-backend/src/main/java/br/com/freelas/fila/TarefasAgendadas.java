package br.com.freelas.fila;

import br.com.freelas.repositorio.AssinaturaRepository;
import br.com.freelas.repositorio.ContaRecebimentoRepository;
import br.com.freelas.repositorio.VagaRepository;
import br.com.freelas.servico.AvaliacaoService;
import br.com.freelas.servico.ContratacaoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Rotinas que rodam a cada minuto (não têm endpoint, KAN-69 RN12):
 * encerrar vagas por prazo, expirar aceites, publicar avaliações cegas vencidas,
 * encerrar quarentenas de chave Pix e expirar assinaturas.
 * Com várias instâncias do backend, as atualizações em lote são seguras para rodar em paralelo.
 */
@Component
public class TarefasAgendadas {

    private static final Logger log = LoggerFactory.getLogger(TarefasAgendadas.class);

    private final VagaRepository vagas;
    private final ContaRecebimentoRepository contas;
    private final AssinaturaRepository assinaturas;
    private final ContratacaoService contratacoes;
    private final AvaliacaoService avaliacoes;

    public TarefasAgendadas(VagaRepository vagas, ContaRecebimentoRepository contas, AssinaturaRepository assinaturas,
                            ContratacaoService contratacoes, AvaliacaoService avaliacoes) {
        this.vagas = vagas;
        this.contas = contas;
        this.assinaturas = assinaturas;
        this.contratacoes = contratacoes;
        this.avaliacoes = avaliacoes;
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void aCadaMinuto() {
        LocalDateTime agora = LocalDateTime.now();
        int aceites = contratacoes.expirarAceitesVencidos();
        int encerradas = vagas.encerrarPorPrazo(agora);
        int concluidas = vagas.concluirTerminadas(agora);
        int avaliacoesPublicadas = avaliacoes.publicarAvaliacoesVencidas();
        int quarentenas = contas.encerrarQuarentenas(agora);
        int expiradas = assinaturas.expirar(agora);
        if (aceites + encerradas + concluidas + avaliacoesPublicadas + quarentenas + expiradas > 0) {
            log.info("Rotina: {} aceites expirados, {} vagas encerradas por prazo, {} vagas concluídas, "
                            + "{} avaliações publicadas, {} quarentenas encerradas, {} assinaturas expiradas",
                    aceites, encerradas, concluidas, avaliacoesPublicadas, quarentenas, expiradas);
        }
    }
}
