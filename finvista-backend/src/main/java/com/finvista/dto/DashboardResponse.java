package com.finvista.dto;

import java.math.BigDecimal;

public record DashboardResponse(

        BigDecimal receita,
        BigDecimal despesa,
        BigDecimal resultado,
        BigDecimal margem,

        BigDecimal receitaMesAnterior,
        BigDecimal despesaMesAnterior,
        BigDecimal resultadoMesAnterior,
        BigDecimal margemMesAnterior,

        BigDecimal variacaoReceita,
        BigDecimal variacaoDespesa,
        BigDecimal variacaoResultado

) {
}