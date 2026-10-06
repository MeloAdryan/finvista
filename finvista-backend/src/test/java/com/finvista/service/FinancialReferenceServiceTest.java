package com.finvista.service;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialReferenceServiceTest {
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Test
    void lancamentoFuturoNaoDefineOMesDoPainel() {
        verificarReferencia(LocalDate.of(2029, 3, 16));
    }

    @Test
    void lancamentoAntigoNaoDefineOMesDoPainel() {
        verificarReferencia(LocalDate.of(2016, 4, 17));
    }

    @Test
    void semLancamentosUsaOMesAtual() {
        verificarReferencia(null);
    }

    private void verificarReferencia(LocalDate maiorData) {
        var aggregation = mock(FinancialTransactionAggregationService.class);
        when(aggregation.obterMaiorData()).thenReturn(maiorData);
        var service = new FinancialReferenceService(aggregation);
        YearMonth antes = YearMonth.now(FUSO);
        YearMonth resultado = service.obterMesReferencia();
        YearMonth depois = YearMonth.now(FUSO);
        assertTrue(resultado.equals(antes) || resultado.equals(depois));
        verify(aggregation, never()).obterMaiorData();
    }
}
