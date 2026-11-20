package com.finvista.integration.contaazul;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class ContaAzulKeyDerivation {

    private ContaAzulKeyDerivation() {
    }

    static byte[] derive(String master, String purpose) {
        try {
            byte[] key = Base64.getDecoder().decode(master);
            if (key.length != 32) {
                throw new IllegalArgumentException();
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("FinVista/ContaAzul/HKDF/v2".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] prk = mac.doFinal(key);
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            mac.update(("FinVista/ContaAzul/" + purpose).getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(new byte[]{1});
        } catch (Exception e) {
            throw new IllegalStateException("Chave da integração inválida.");
        }
    }
}
