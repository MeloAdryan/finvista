package com.finvista.integration.contaazul;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@org.springframework.context.annotation.PropertySource("classpath:conta-azul.properties")
@Component
@ConfigurationProperties(prefix = "finvista.conta-azul")
public class ContaAzulProperties {

    private boolean enabled = false;
    private String redirectUri;
    private String encryptionKey;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String value) {
        redirectUri = value;
    }

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public void setEncryptionKey(String value) {
        encryptionKey = value;
    }
    private String clientId;
    private String clientSecret;
    private String apiUrl;
    private String authorizationUrl;
    private String tokenUrl;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getAuthorizationUrl() {
        return authorizationUrl;
    }

    public void setAuthorizationUrl(String authorizationUrl) {
        this.authorizationUrl = authorizationUrl;
    }

    public String getTokenUrl() {
        return tokenUrl;
    }

    public void setTokenUrl(String tokenUrl) {
        this.tokenUrl = tokenUrl;
    }
}
