package com.finvista.integration.contaazul;

public class ContaAzulAccountsException extends IllegalStateException {

    private final String codigo;
    private final String etapa;
    private final Integer httpStatus;

    public ContaAzulAccountsException(String codigo, String etapa, Integer httpStatus) {
        super(codigo + " (" + etapa + ")");
        this.codigo = codigo;
        this.etapa = etapa;
        this.httpStatus = httpStatus;
    }

    public String codigo() {
        return codigo;
    }

    public String etapa() {
        return etapa;
    }

    public Integer httpStatus() {
        return httpStatus;
    }
}
   