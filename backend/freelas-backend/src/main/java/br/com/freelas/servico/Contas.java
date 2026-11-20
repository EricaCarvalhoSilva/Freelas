package br.com.freelas.servico;

import br.com.freelas.entidade.Usuario;
import br.com.freelas.enums.StatusUsuario;
import br.com.freelas.excecao.AcessoNegadoException;

/** Regras de status da conta usadas por vários serviços. */
public final class Contas {
    private Contas() {}

    /** Conta suspensa ou com e-mail pendente só consulta (endpoints Empresa 2.2). */
    public static void exigirAtiva(Usuario usuario) {
        if (usuario.getStatus() != StatusUsuario.ATIVO) {
            throw new AcessoNegadoException(switch (usuario.getStatus()) {
                case PENDENTE -> "Confirme o seu e-mail para liberar esta operação.";
                case SUSPENSO -> "Conta suspensa: só é possível consultar dados.";
                default -> "Conta sem permissão para esta operação (status " + usuario.getStatus() + ").";
            });
        }
    }

    /** O {id} do caminho precisa ser o mesmo do token (RNF07). */
    public static void exigirMesmoUsuario(Long idDoCaminho, Long idDoToken) {
        if (!idDoCaminho.equals(idDoToken)) {
            throw new AcessoNegadoException("Você só pode acessar os dados da sua própria conta.");
        }
    }
}
