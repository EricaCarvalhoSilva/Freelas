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

public interface FreelancerRepository extends JpaRepository<Freelancer, Long> {
    boolean existsByCpf(String cpf);

    /**
     * Elegíveis para uma substituição: mesma função, conta ativa, sem suspensão e na região.
     * A região é aproximada pelo endereço (UF, cidade, início do CEP) porque o banco não guarda coordenadas.
     */
    @Query("""
           select distinct f from Freelancer f, FreelancerFuncao ff
           join f.endereco e
           where ff.freelancer = f and ff.funcao.id = :funcaoId
             and f.status = br.com.freelas.enums.StatusUsuario.ATIVO
             and (f.suspensoAte is null or f.suspensoAte < :agora)
             and f.id <> :excluirId
             and e.uf = :uf
             and (:cidade is null or lower(e.cidade) = :cidade)
             and (:cepPrefixo is null or e.cep like :cepPrefixo)
           """)
    List<Freelancer> elegiveisParaSubstituicao(@Param("funcaoId") Integer funcaoId, @Param("excluirId") Long excluirId,
                                               @Param("agora") LocalDateTime agora, @Param("uf") String uf,
                                               @Param("cidade") String cidade, @Param("cepPrefixo") String cepPrefixo);
}
