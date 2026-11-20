package br.com.freelas.config;

import br.com.freelas.entidade.Administrador;
import br.com.freelas.enums.StatusUsuario;
import br.com.freelas.repositorio.AdministradorRepository;
import br.com.freelas.repositorio.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria o primeiro Administrador quando ADMIN_EMAIL e ADMIN_SENHA estão definidos.
 * Não existe senha padrão no código.
 */
@Component
public class AdminInicial implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInicial.class);

    private final PropriedadesFreelas props;
    private final UsuarioRepository usuarios;
    private final AdministradorRepository administradores;
    private final PasswordEncoder passwordEncoder;

    public AdminInicial(PropriedadesFreelas props, UsuarioRepository usuarios,
                        AdministradorRepository administradores, PasswordEncoder passwordEncoder) {
        this.props = props;
        this.usuarios = usuarios;
        this.administradores = administradores;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var admin = props.admin();
        if (admin == null || admin.email() == null || admin.email().isBlank() || admin.senha() == null || admin.senha().isBlank()) {
            return;
        }
        if (usuarios.existsByEmailIgnoreCase(admin.email())) return;
        Administrador a = new Administrador();
        a.setNome("Administrador");
        a.setEmail(admin.email().toLowerCase());
        a.setSenhaHash(passwordEncoder.encode(admin.senha()));
        a.setTelefone("0000000000");
        a.setStatus(StatusUsuario.ATIVO);
        a.setEmailConfirmado(true);
        administradores.save(a);
        log.info("Administrador inicial criado: {}", a.getEmail());
    }
}
