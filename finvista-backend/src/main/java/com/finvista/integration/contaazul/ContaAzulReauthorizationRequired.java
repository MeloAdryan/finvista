package com.finvista.integration.contaazul;

public final class ContaAzulReauthorizationRequired extends IllegalStateException {
    public ContaAzulReauthorizationRequired() {
        super("A autorização Conta Azul expirou ou foi revogada. Autorize novamente esta empresa para retomar a automação.");
    }
}
