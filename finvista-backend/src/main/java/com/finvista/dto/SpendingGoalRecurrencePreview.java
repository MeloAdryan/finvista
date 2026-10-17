package com.finvista.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SpendingGoalRecurrencePreview(String recorrencia, BigDecimal limiteTotal,
        List<Periodo> periodos) {

    public record Periodo(LocalDate inicio, LocalDate fim, BigDecimal limite,
            long dias, long diasPeriodoCompleto, boolean proporcional) {}
}    