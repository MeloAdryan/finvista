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
        assertEquals(1, r.ambiguidades().size());
    }

    @Test
    void equalTotalsWithDifferentMovementsDoNotMatch() {
        var r = FinancialMovementMatcher.compare(List.of(m("a", "b1", "60"), m("b", "b2", "40")), List.of(m("f", null, "50"), m("g", null, "50")));
        assertTrue(r.correspondencias().isEmpty());
        assertEquals(2, r.somenteApi().size());
    }
}
