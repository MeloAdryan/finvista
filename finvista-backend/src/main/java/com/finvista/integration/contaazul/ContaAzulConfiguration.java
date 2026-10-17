package com.finvista.integration.contaazul;

import java.net.URI;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class ContaAzulConfiguration {

    private final ContaAzulProperties p;

    public ContaAzulConfiguration(ContaAzulProperties p) {
        this.p = p;
    }

    public boolean pronta() {
        try {
            validar();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public void validar() {
        if (p.getClientId() == null || p.getClientId().isBlank() || p.getClientSecret() == null || p.getClientSecret().isBlank()) {
            throw new IllegalStateException("Credenciais Conta Azul não configuradas no backend.");
        }
        try {
            URI u = URI.create(p.getRedirectUri());
            if (!"https".equals(u.getScheme()) || u.getHost() == null || u.getUserInfo() != null || u.getFragment() != null || u.getQuery() != null || !u.getPath().endsWith("/api/integracoes/conta-azul/callback")) {
                throw new IllegalArgumentException();

            }
        } catch (Exception e) {
            throw new IllegalStateException("Configure uma URL HTTPS de callback do FinVista, sem query ou fragmento.");
        }
        try {
            if (Base64.getDecoder().decode(p.getEncryptionKey()).length != 32) {
                throw new IllegalArgumentException();

            }
        } catch (Exception e) {
            throw new IllegalStateException("Configure uma chave Base64 de criptografia com 32 bytes.");
        }
    }

    public void exigirAtiva() {
        if (!p.isEnabled()) {
            throw new IllegalStateException("Conexão Conta Azul desabilitada. A importação por arquivo continua disponível.");

        }
        validar();
    }
}
