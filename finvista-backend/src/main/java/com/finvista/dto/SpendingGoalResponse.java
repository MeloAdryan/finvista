package com.finvista.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SpendingGoalResponse(
        Long id,
        String tipo,
        LocalDate dataInicio,
        LocalDate dataFim,
        BigDecimal valorLimite,
        BigDecimal gastoAtual,
        BigDecimal percentualUtilizado,
        BigDecimal saldoMeta,
        Integer percentualAlerta,
        String status
) {
}