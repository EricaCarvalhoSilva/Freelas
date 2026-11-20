package br.com.freelas.seguranca;

import br.com.freelas.config.PropriedadesFreelas;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/** Criptografia AES-256-GCM da chave Pix e dos dados bancários (RNF21). */
@Service
public class CriptografiaService {

    private static final int IV_BYTES = 12;
    private final SecretKey chave;
    private final SecureRandom random = new SecureRandom();

    public CriptografiaService(PropriedadesFreelas props) {
        byte[] bytes = Base64.getDecoder().decode(props.seguranca().chaveCriptografia());
        if (bytes.length != 32) {
            throw new IllegalStateException("freelas.seguranca.chave-criptografia precisa ter 32 bytes em Base64");
        }
        this.chave = new SecretKeySpec(bytes, "AES");
    }

    public String criptografar(String texto) {
        if (texto == null) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(128, iv));
            byte[] cifrado = cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array());
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao criptografar", e);
        }
    }

    public String descriptografar(String base64) {
        if (base64 == null) return null;
        try {
            byte[] tudo = Base64.getDecoder().decode(base64);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(128, tudo, 0, IV_BYTES));
            return new String(cipher.doFinal(tudo, IV_BYTES, tudo.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao descriptografar", e);
        }
    }

    /** Mostra só o fim do dado: "***********1234". */
    public static String mascarar(String valor) {
        if (valor == null) return null;
        int visiveis = Math.min(4, valor.length() / 3);
        return "*".repeat(valor.length() - visiveis) + valor.substring(valor.length() - visiveis);
    }
}
