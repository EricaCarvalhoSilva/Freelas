package br.com.freelas.dto;

import br.com.freelas.entidade.Assinatura;
import br.com.freelas.entidade.Impulsionamento;
import br.com.freelas.entidade.Plano;
import br.com.freelas.enums.*;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** DTOs de Premium, destaque de vaga e Boost (UC14, UC15, RF18, RF19; Regras de Premium e Boost). */
public final class MonetizacaoDto {
    private MonetizacaoDto() {}

    public record PlanoResposta(Integer id, String nome, PublicoPlano publico, BigDecimal preco, Integer duracaoDias) {
        public static PlanoResposta de(Plano p) {
            return new PlanoResposta(p.getId(), p.getNome(), p.getPublico(), p.getPreco(), p.getDuracaoDias().intValue());
        }
    }

    public record AssinaturaEmpresaRequest(@NotNull Integer planoId, @NotNull Long meioPagamentoId) {}

    public record AssinaturaFreelancerRequest(@NotNull Integer planoId) {}

    public record RenovacaoRequest(@NotNull Boolean renovacaoAutomatica) {}

    public record AssinaturaResposta(Long id, PlanoResposta plano, LocalDateTime inicio, LocalDateTime fim,
                                     Boolean renovacaoAutomatica, StatusAssinatura status, String idTransacaoGateway) {
        public static AssinaturaResposta de(Assinatura a) {
            return new AssinaturaResposta(a.getId(), PlanoResposta.de(a.getPlano()), a.getInicio(), a.getFim(),
                    a.getRenovacaoAutomatica(), a.getStatus(), a.getIdTransacaoGateway());
        }
    }

    public record OpcaoResposta(OpcaoDestaque opcao, TipoImpulsionamento tipo, long duracaoHoras, BigDecimal preco) {
        public static OpcaoResposta de(OpcaoDestaque o) {
            return new OpcaoResposta(o, o.getTipo(), o.getDuracao().toHours(), o.getPreco());
        }
    }

    public record DestaqueRequest(@NotNull OpcaoDestaque opcao) {}

    /** O Boost começa na hora: o freelancer precisa aceitar o aviso de execução imediata. */
    public record BoostRequest(
            OpcaoDestaque opcao,
            @NotNull @AssertTrue(message = "É preciso aceitar o aviso de execução imediata") Boolean aceiteExecucaoImediata) {}

    public record ImpulsionamentoResposta(Long id, TipoImpulsionamento tipo, Long vagaId, Long freelancerId,
                                          LocalDateTime inicio, LocalDateTime fim, BigDecimal valor, Boolean ativo,
                                          String idTransacaoGateway) {
        public static ImpulsionamentoResposta de(Impulsionamento i) {
            LocalDateTime agora = LocalDateTime.now();
            return new ImpulsionamentoResposta(i.getId(), i.getTipo(),
                    i.getVaga() == null ? null : i.getVaga().getId(),
                    i.getFreelancer() == null ? null : i.getFreelancer().getId(),
                    i.getInicio(), i.getFim(), i.getValor(), !agora.isBefore(i.getInicio()) && agora.isBefore(i.getFim()),
                    i.getIdTransacaoGateway());
        }
    }
}
