package com.finvista.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProjectionPlanningResponse(
        LocalDate inicio, LocalDate fim, LocalDate hoje,
        BigDecimal mediaReceitaMensal, int mesesHistorico,
        BigDecimal receitaMensalEsperada, BigDecimal saldoInicial,
        BigDecimal variacaoPercentual, List<Mes> meses
) {
    public record Mes(
            String periodo, BigDecimal receitaRegistrada,
            BigDecimal receitaComplementar, BigDecimal receitaSimulada,
            BigDecimal despesaRegistrada, BigDecimal resultadoMensal,
            BigDecimal resultadoAcumulado, BigDecimal saldoBase,
            BigDecimal saldoConservador, BigDecimal saldoOtimista
    ) {}
}
