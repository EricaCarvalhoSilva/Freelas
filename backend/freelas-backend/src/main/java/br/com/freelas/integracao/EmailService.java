package br.com.freelas.integracao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Envio de e-mail. Em desenvolvimento só escreve no log (o código de confirmação aparece no console).
 * Para produção, trocar por spring-boot-starter-mail ou pelo provedor escolhido pelo grupo.
 */
@Component
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    public void enviar(String para, String assunto, String texto) {
        log.info("[e-mail] para={} | assunto={} | {}", para, assunto, texto);
    }
}
