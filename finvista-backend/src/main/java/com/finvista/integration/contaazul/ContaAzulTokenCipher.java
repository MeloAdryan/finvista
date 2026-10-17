package com.finvista.integration.contaazul;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.springframework.stereotype.Component;

@Component
public class ContaAzulTokenCipher {

    private final ContaAzulProperties p;
    private final SecureRandom random = new SecureRandom();

    public ContaAzulTokenCipher(ContaAzulProperties p) {
        this.p = p;
    }

    private byte[] aad(Long cliente, String tipo) {
        return (cliente + ":" + tipo).getBytes(StandardCharsets.UTF_8);
    }

    public String cifrar(String token, Long cliente, String tipo) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(Base64.getDecoder().decode(p.getEncryptionKey()), "AES"), new GCMParameterSpec(128, iv));
            c.updateAAD(aad(cliente, tipo));
            byte[] text = c.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return "v1." + Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(text);
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível proteger as credenciais Conta Azul.");
        }
    }

    public String decifrar(String token, Long cliente, String tipo) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3 || !parts[0].equals("v1")) {
                throw new IllegalArgumentException();

            }
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(Base64.getDecoder().decode(p.getEncryptionKey()), "AES"), new GCMParameterSpec(128, Base64.getDecoder().decode(parts[1])));
            c.updateAAD(aad(cliente, tipo));
            return new String(c.doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível ler as credenciais Conta Azul. Confira a chave do backend.");
        }
    }
}
