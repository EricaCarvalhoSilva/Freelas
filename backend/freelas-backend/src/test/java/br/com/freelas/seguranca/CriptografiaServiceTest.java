package br.com.freelas.seguranca;

import br.com.freelas.config.PropriedadesFreelas;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CriptografiaServiceTest {

    private final CriptografiaService cripto = new CriptografiaService(new PropriedadesFreelas(
            new PropriedadesFreelas.Seguranca("x".repeat(32), 15, 30, 7, 15,
                    Base64.getEncoder().encodeToString("chave-de-teste-com-32-bytes-ok!!".getBytes()), "segredo"),
            null, null, null));

    @Test
    void criptografaEDescriptografaAChavePix() {
        String cifrado = cripto.criptografar("ana@teste.com");
        assertNotEquals("ana@teste.com", cifrado);
        assertEquals("ana@teste.com", cripto.descriptografar(cifrado));
    }

    @Test
    void mesmoTextoGeraCifrasDiferentes() {
        assertNotEquals(cripto.criptografar("123"), cripto.criptografar("123"));
    }

    @Test
    void hmacDoWebhookConfere() {
        String assinatura = Hashes.hmacSha256("{\"a\":1}", "segredo");
        assertTrue(Hashes.iguais(assinatura, Hashes.hmacSha256("{\"a\":1}", "segredo")));
        assertFalse(Hashes.iguais(assinatura, Hashes.hmacSha256("{\"a\":2}", "segredo")));
    }
}
