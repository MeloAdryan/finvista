package com.finvista.service;

import com.finvista.service.FinancialTransactionAggregationService.MonthlyFinancialSummary;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectionPlanningServiceTest {

    private final FinancialTransactionAggregationService aggregation = mock(FinancialTransactionAggregationService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-08T15:00:00Z"), ZoneOffset.UTC);
    private final ProjectionPlanningService service = new ProjectionPlanningService(aggregation, clock);

    private BigDecimal v(String valor) {
        return new BigDecimal(valor);
    }

    private MonthlyFinancialSummary mes(int ano, int numero, String receita, String despesa) {
        return new MonthlyFinancialSummary(YearMonth.of(ano, numero), v(receita), v(despesa));
    }

    private void registros(MonthlyFinancialSummary... meses) {
        when(aggregation.obterResumoMensal(LocalDate.of(2026, 7, 1), LocalDate.of(2027, 3, 31))).thenReturn(List.of(meses));
    }

    @Test
    void mediaConsideraTresMesesAnterioresComMesSemReceita() {
        registros(mes(2026, 7, "90", "0"), mes(2026, 9, "30", "0"), mes(2026, 10, "999", "0"));
        var r = service.simular(null, null, null);
        assertEquals(v("40.00"), r.mediaReceitaMensal());
        assertEquals(v("40.00"), r.receitaMensalEsperada());
        assertEquals(6, r.meses().size());
        verify(aggregation).obterResumoMensal(LocalDate.of(2026, 7, 1), LocalDate.of(2027, 3, 31));
    }

    @Test
    void receitaEsperadaCompletaSemDuplicarRegistro() {
        registros(mes(2026, 10, "40.00", "20.00"));
        var m = service.simular(v("100.00"), v("0"), v("20")).meses().get(0);
        assertEquals(v("60.00"), m.receitaComplementar());
        assertEquals(v("100.00"), m.receitaSimulada());
        assertEquals(v("80.00"), m.resultadoMensal());
        assertEquals(v("60.00"), m.saldoConservador());
        assertEquals(v("100.00"), m.saldoOtimista());
    }

    @Test
    void receitaJaRegistradaNaoEhReduzidaPelosCenarios() {
        registros(mes(2026, 10, "130.00", "20.00"));
        var m = service.simular(v("100.00"), null, v("20")).meses().get(0);
        assertEquals(0, m.receitaComplementar().signum());
        assertEquals(v("110.00"), m.saldoBase());
        assertEquals(m.saldoBase(), m.saldoConservador());
        assertEquals(m.saldoBase(), m.saldoOtimista());
    }

    @Test
    void saldoInicialFicaSeparadoDoResultadoAcumulado() {
        registros(mes(2026, 10, "0.00", "20.00"));
        var r = service.simular(v("100.00"), v("1000.00"), v("20"));
        assertEquals(v("80.00"), r.meses().get(0).resultadoAcumulado());
        assertEquals(v("1080.00"), r.meses().get(0).saldoBase());
        for (var m : r.meses()) {
            assertEquals(r.saldoInicial().add(m.resultadoAcumulado()), m.saldoBase());
            assertTrue(m.saldoConservador().compareTo(m.saldoBase()) <= 0);
            assertTrue(m.saldoBase().compareTo(m.saldoOtimista()) <= 0);
        }
    }

    @Test
    void somenteRegistrosMantemDeficitSemInventarReceita() {
        registros(mes(2026, 10, "0.00", "74275.85"), mes(2026, 11, "0.00", "100.00"));
        var r = service.simular(v("0"), v("0"), v("0"));
        assertEquals(v("-74375.85"), r.meses().get(5).saldoBase());
        assertEquals(r.meses().get(5).saldoBase(), r.meses().get(5).resultadoAcumulado());
        assertTrue(r.meses().stream().allMatch(m -> m.receitaComplementar().signum() == 0));
    }

    @Test
    void somenteRegistrosPreservaEstornoDeReceita() {
        registros(mes(2026, 10, "-50.00", "10.00"));
        var r = service.simular(v("0"), v("0"), v("0"));
        var m = r.meses().get(0);
        assertEquals(v("-50.00"), m.receitaSimulada());
        assertEquals(0, m.receitaComplementar().signum());
        assertEquals(v("-60.00"), m.resultadoMensal());
    }

    @Test
    void mediaLiquidaNegativaSugereReceitaZero() {
        registros(mes(2026, 7, "-30.00", "0"));
        var r = service.simular(null, null, null);
        assertEquals(v("-10.00"), r.mediaReceitaMensal());
        assertEquals(0, r.receitaMensalEsperada().signum());
    }

    @Test
    void rejeitaValoresInvalidosAntesDeConsultarDados() {
        assertThrows(IllegalArgumentException.class, () -> service.simular(v("-1"), null, null));
        assertThrows(IllegalArgumentException.class, () -> service.simular(v("1.001"), null, null));
        assertThrows(IllegalArgumentException.class, () -> service.simular(null, v("10000000000000"), null));
        assertThrows(IllegalArgumentException.class, () -> service.simular(null, null, v("101")));
        verifyNoInteractions(aggregation);
    }

    @Test
    void referenciaUsaSaoPauloNaViradaDoMes() {
        var saoPaulo = new ProjectionPlanningService(aggregation, Clock.fixed(Instant.parse("2026-11-01T02:00:00Z"), ZoneOffset.UTC));
        registros();
        var r = saoPaulo.simular(null, null, null);
        assertEquals(LocalDate.of(2026, 10, 31), r.hoje());
        assertEquals(LocalDate.of(2026, 10, 1), r.inicio());
    }
}
