package br.com.freelas.repositorio;

import br.com.freelas.entidade.*;
import br.com.freelas.enums.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ContratacaoRepository extends JpaRepository<Contratacao, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Contratacao c where c.id = :id")
    Optional<Contratacao> buscarComLock(@Param("id") Long id);

    Optional<Contratacao> findByCandidaturaId(Long candidaturaId);

    long countByCandidaturaVagaIdAndStatusIn(Long vagaId, Collection<StatusContratacao> status);

    List<Contratacao> findByCandidaturaVagaIdAndStatusIn(Long vagaId, Collection<StatusContratacao> status);

    boolean existsByCandidaturaVagaEmpresaIdAndStatusIn(Long empresaId, Collection<StatusContratacao> status);

    boolean existsByCandidaturaFreelancerIdAndStatusIn(Long freelancerId, Collection<StatusContratacao> status);

    @Query("""
           select count(c) > 0 from Contratacao c join c.candidatura ca join ca.vaga v
           where ca.freelancer.id = :freelancerId and v.empresa.id = :empresaId and c.confirmadaEm is not null
           """)
    boolean existsConfirmadaEntre(@Param("freelancerId") Long freelancerId, @Param("empresaId") Long empresaId);

    long countByCandidaturaFreelancerIdAndStatus(Long freelancerId, StatusContratacao status);

    @Query("""
           select c from Contratacao c join c.candidatura ca join ca.vaga v
           where v.empresa.id = :empresaId
             and (:status is null or c.status = :status)
             and (:vagaId is null or v.id = :vagaId)
           """)
    Page<Contratacao> listarDaEmpresa(@Param("empresaId") Long empresaId,
                                      @Param("status") StatusContratacao status,
                                      @Param("vagaId") Long vagaId, Pageable pageable);

    @Query("""
           select c from Contratacao c join c.candidatura ca
           where ca.freelancer.id = :freelancerId and (:status is null or c.status = :status)
           """)
    Page<Contratacao> listarDoFreelancer(@Param("freelancerId") Long freelancerId,
                                         @Param("status") StatusContratacao status, Pageable pageable);

    /** Conflito de horário: o freelancer já tem turno ativo que cruza o período. */
    @Query("""
           select count(c) > 0 from Contratacao c join c.candidatura ca join ca.vaga v
           where ca.freelancer.id = :freelancerId
             and c.status in (br.com.freelas.enums.StatusContratacao.AGUARDANDO_ACEITE,
                              br.com.freelas.enums.StatusContratacao.CONFIRMADA,
                              br.com.freelas.enums.StatusContratacao.EM_ANDAMENTO)
             and v.inicio < :fim and v.fim > :inicio
             and (:ignorarId is null or c.id <> :ignorarId)
           """)
    boolean temConflitoDeHorario(@Param("freelancerId") Long freelancerId,
                                 @Param("inicio") LocalDateTime inicio,
                                 @Param("fim") LocalDateTime fim,
                                 @Param("ignorarId") Long ignorarId);

    /** Contratações das últimas 72 h ainda sem avaliação do freelancer. */
    @Query("""
           select c from Contratacao c join c.candidatura ca
           where ca.freelancer.id = :freelancerId
             and c.status = br.com.freelas.enums.StatusContratacao.CONCLUIDA
             and c.encerradaEm >= :desde
             and not exists (select a from Avaliacao a where a.contratacao = c
                             and a.direcao = br.com.freelas.enums.DirecaoAvaliacao.FREELA_P_EMPRESA)
           """)
    List<Contratacao> pendentesDeAvaliacaoDoFreelancer(@Param("freelancerId") Long freelancerId,
                                                        @Param("desde") LocalDateTime desde);

    /** Aceite que não aconteceu antes do início do turno. */
    @Query("""
           select c from Contratacao c join c.candidatura ca join ca.vaga v
           where c.status = br.com.freelas.enums.StatusContratacao.AGUARDANDO_ACEITE and v.inicio < :agora
           """)
    List<Contratacao> aceitesVencidos(@Param("agora") LocalDateTime agora);

    @Query("""
           select coalesce(sum(p.valorLiquido), 0) from Pagamento p join p.contratacao c join c.candidatura ca
           where ca.freelancer.id = :freelancerId
             and p.status = br.com.freelas.enums.StatusPagamento.LIBERADO
             and p.liberadoEm >= :desde
           """)
    BigDecimal somarGanhosLiberados(@Param("freelancerId") Long freelancerId, @Param("desde") LocalDateTime desde);

    @Query("""
           select c from Contratacao c join c.candidatura ca
           where ca.freelancer.id = :freelancerId
             and c.status = br.com.freelas.enums.StatusContratacao.CONCLUIDA
             and c.encerradaEm >= :desde
           """)
    List<Contratacao> concluidasDoFreelancerDesde(@Param("freelancerId") Long freelancerId, @Param("desde") LocalDateTime desde);
}
