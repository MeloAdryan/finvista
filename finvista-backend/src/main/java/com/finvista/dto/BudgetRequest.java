package com.finvista.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BudgetRequest(
        String nome,
        String centroCusto,
        String categoria,
        BigDecimal valorPlanejado,
        LocalDate dataInicio,
        LocalDate dataFim
) {
}