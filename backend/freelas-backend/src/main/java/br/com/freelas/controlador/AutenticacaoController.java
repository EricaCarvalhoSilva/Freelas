package br.com.freelas.controlador;

import br.com.freelas.dto.AutenticacaoDto.*;
import br.com.freelas.servico.AutenticacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Autenticação compartilhada por Empresa, Freelancer e Administrador (UC01, UC02, UC04). */
@RestController
@RequestMapping("/autenticacao")
@Tag(name = "Autenticação")
public class AutenticacaoController {

    private final AutenticacaoService servico;

    public AutenticacaoController(AutenticacaoService servico) {
        this.servico = servico;
    }

    @PostMapping("/login")
    @Operation(summary = "Entrar com e-mail e senha; devolve o token de acesso e o de renovação")
    public TokenResposta login(@Valid @RequestBody LoginRequest req) {
        return servico.login(req);
    }

    @PostMapping("/renovacao")
    @Operation(summary = "Trocar o token de renovação por um novo par de tokens")
    public TokenResposta renovar(@Valid @RequestBody RenovacaoRequest req) {
        return servico.renovar(req);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Sair e invalidar o token de renovação")
    public void logout(@Valid @RequestBody RenovacaoRequest req) {
        servico.logout(req);
    }

    @PostMapping("/confirmacao-email")
    @Operation(summary = "Confirmar o e-mail (cadastro ou troca de e-mail) com o código de 6 dígitos")
    public MensagemResposta confirmarEmail(@Valid @RequestBody ConfirmacaoEmailRequest req) {
        return servico.confirmarEmail(req);
    }

    @PostMapping("/confirmacao-email/reenvio")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Reenviar o código de confirmação de e-mail")
    public MensagemResposta reenviar(@Valid @RequestBody EmailRequest req) {
        return servico.reenviarConfirmacao(req);
    }

    @PostMapping("/recuperacao-senha")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Esqueci a senha: envia um código válido por 15 minutos")
    public MensagemResposta recuperar(@Valid @RequestBody EmailRequest req) {
        return servico.solicitarRecuperacao(req);
    }

    @PostMapping("/redefinicao-senha")
    @Operation(summary = "Definir a nova senha com o código recebido")
    public MensagemResposta redefinir(@Valid @RequestBody RedefinicaoSenhaRequest req) {
        return servico.redefinirSenha(req);
    }
}
