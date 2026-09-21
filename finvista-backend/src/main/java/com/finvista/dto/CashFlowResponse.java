package com.finvista.dto;

import java.math.BigDecimal;

public record CashFlowResponse(
        String mes,
        BigDecimal saldoInicial,
        BigDecimal entradas,
        BigDecimal saidas,
        BigDecimal saldoFinal
) {
}