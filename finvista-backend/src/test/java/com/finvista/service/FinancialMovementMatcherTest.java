package com.finvista.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FinancialMovementMatcherTest {

    FinancialMovementMatcher.Movement m(String key, String id, String value) {
        return new FinancialMovementMatcher.Movement(key, "parcela", id, "Descrição", "DESPESA", LocalDate.of(2026, 9, 10), new BigDecimal(value));
    }

    @Test
    void idDateAndValueMatch() {
        var r = FinancialMovementMatcher.compare(List.of(m("a", "baixa", "100")), List.of(m("f", "baixa", "100.00")));
        assertEquals("ID_BAIXA_DATA_TIPO_VALOR", r.correspondencias().get(0).criterio());
        assertTrue(r.somenteApi().isEmpty());
    }

    @Test
    void equalAmountsWithoutIdAreOnlySuggestions() {
        var r = FinancialMovementMatcher.compare(List.of(m("a", "baixa", "100")), List.of(m("f", null, "100")));
        assertTrue(r.correspondencias().get(0).criterio().startsWith("SUGESTAO"));
    }

    @Test
    void duplicatePaymentsRemainAmbiguous() {
        var r = FinancialMovementMatcher.compare(List.of(m("a", "b1", "100"), m("b", "b2", "100")), List.of(m("f", null, "100")));
        assertTrue(r.correspondencias().isEmpty());
        assertEquals(2, r.somenteApi().size());
        assertEquals(1, r.ambiguidades().size());
    }

    @Test
    void duplicateFileRowsCannotConsumeSamePaymentTwice() {
        var r = FinancialMovementMatcher.compare(List.of(m("a", "b", "100")), List.of(m("f", null, "100"), m("g", null, "100")));
        assertTrue(r.correspondencias().isEmpty());
        assertEquals(2, r.somenteExtrato().size());
    }

    @Test
    void conflictingIdsNeverFallBackToAmount() {
        var r = FinancialMovementMatcher.compare(List.of(m("a", "b1", "100")), List.of(m("f", "b2", "100")));
        assertTrue(r.correspondencias().isEmpty());
    }

    @Test
    void sameIdWithDifferentValueIsVisible() {
        var r = FinancialMovementMatcher.compare(List.of(m("a", "b1", "100")), List.of(m("f", "b1", "99")));
        assertTrue(r.correspondencias().isEmpty());
        assertEquals(1, r.divergencias().size());
        assertTrue(r.divergencias().get(0).campos().contains("Valor líquido"));
    }

    @Test
    void equalTotalsWithDifferentMovementsDoNotMatch() {
        var r = FinancialMovementMatcher.compare(List.of(m("a", "b1", "60"), m("b", "b2", "40")), List.of(m("f", null, "50"), m("g", null, "50")));
        assertTrue(r.correspondencias().isEmpty());
        assertEquals(2, r.somenteApi().size());
    }

    @Test
    void conflitoPorIdNaoPodeSerReaproveitadoEmSugestaoSemId() {
        var r = FinancialMovementMatcher.compare(List.of(m("a", "b", "100")), List.of(m("errado", "b", "99"), m("sem-id", null, "100")));
        assertTrue(r.correspondencias().isEmpty());
        assertEquals(1, r.divergencias().size());
        assertEquals(1, r.somenteExtrato().size());
    }

    @Test
    void mesmoIdApontaDataTipoParcelaEContaDiferentes() {
        var a = new FinancialMovementMatcher.Movement("a", "p1", "id", "A", "RECEITA", LocalDate.of(2026, 9, 1), new BigDecimal("100"), "conta-a");
        var b = new FinancialMovementMatcher.Movement("f", "p2", "ID ", "B", "DESPESA", LocalDate.of(2026, 9, 2), new BigDecimal("100"), "conta-b");
        var r = FinancialMovementMatcher.compare(List.of(a), List.of(b));
        assertEquals(Set.of("Tipo", "Data", "Parcela", "Conta financeira"), new HashSet<>(r.divergencias().get(0).campos()));
        assertEquals(0, r.divergencias().get(0).diferencaValor().signum());
        assertTrue(r.correspondencias().isEmpty());
    }

    @Test
    void idsSaoComparadosSemDiferencaDeCaixaOuEspaco() {
        var a = new FinancialMovementMatcher.Movement("a", "PARCELA", "ID", "A", "DESPESA", LocalDate.of(2026, 9, 10), new BigDecimal("100"), "CONTA");
        var b = new FinancialMovementMatcher.Movement("f", "parcela ", " id ", "B", "DESPESA", LocalDate.of(2026, 9, 10), new BigDecimal("100.00"), "conta ");
        var r = FinancialMovementMatcher.compare(List.of(a), List.of(b));
        assertEquals(1, r.correspondencias().size());
        assertTrue(r.correspondencias().get(0).contaConferida());
    }

    @Test
    void contaAusenteNaoEhCertificadaNemInferida() {
        var r = FinancialMovementMatcher.compare(List.of(m("a", "id", "100")), List.of(m("f", "id", "100")));
        assertFalse(r.correspondencias().get(0).contaConferida());
    }

    @Test
    void parcelaExplicitaImpedeSugestaoComOutraParcela() {
        var file = new FinancialMovementMatcher.Movement("f", "outra", null, "B", "DESPESA", LocalDate.of(2026, 9, 10), new BigDecimal("100"));
        assertTrue(FinancialMovementMatcher.compare(List.of(m("a", "id", "100")), List.of(file)).correspondencias().isEmpty());
    }

    @Test
    void chavesRepetidasNaMesmaFonteNaoSaoSilenciosamenteDescartadas() {
        assertThrows(IllegalArgumentException.class, () -> FinancialMovementMatcher.compare(List.of(m("a", "b1", "100"), m("a", "b2", "100")), List.of()));
    }

    @Test
    void valorNegativoNaConferenciaEhExpostoComoDiferencaSemGravar() {
        var r = FinancialMovementMatcher.compare(List.of(m("a", "id", "100")), List.of(m("f", "id", "-100")));
        assertEquals(1, r.divergencias().size());
        assertEquals(0, new BigDecimal("200").compareTo(r.divergencias().get(0).diferencaValor()));
    }
}
