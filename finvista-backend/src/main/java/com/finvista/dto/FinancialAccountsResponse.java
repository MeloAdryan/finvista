package com.finvista.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record FinancialAccountsResponse(Long clienteId, String estado, BigDecimal saldo,
        Instant consultadoEm, boolean desatualizado, String mensagem, int contasExcluidas, List<Conta> contas, Diagnostico diagnostico) {

    public FinancialAccountsResponse(Long clienteId, String estado, BigDecimal saldo, Instant consultadoEm,
            boolean desatualizado, String mensagem, int contasExcluidas, List<Conta> contas) {
        this(clienteId, estado, saldo, consultadoEm, desatualizado, mensagem, contasExcluidas, contas, null);
    }

    public record Diagnostico(String codigo, String etapa, Integer httpStatus) {}

    public record Conta(String id, String nome, String tipo, BigDecimal saldo) {}
}