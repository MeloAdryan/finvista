package com.finvista.service;

import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StatementCashPolicyTest {

    final LocalDate today = LocalDate.of(2026, 10, 30);

    ContaAzulStatementService.Line line(String date, String origin, String status, Map<String, String> fields) {
        return new ContaAzulStatementService.Line(2, LocalDate.parse(date), null, null, "Pagamento", "Despesa", origin, "Banco", status,
                new BigDecimal("-189.20"), List.of(), List.of(), List.of(), List.of(), fields);
    }

    @Test
    void dataAntigaNaoEProvaDeErro() {
        assertNull(StatementCashPolicy.exclusion(line("2016-04-17", "Lançamento Financeiro", "Quitado", Map.of()), today));
    }

    @Test
    void inicioHistoricoConferidoExcluiSemApagarOriginal() {
        var l = line("2016-04-17", "Lançamento Financeiro", "Quitado", Map.of(StatementCashPolicy.HISTORICAL_START, "2026-01-01"));
        assertNotNull(StatementCashPolicy.exclusion(l, today));
        assertEquals(LocalDate.of(2016, 4, 17), l.movimento());
    }

    @Test
    void limiteHistoricoInclusivo() {
        assertNull(StatementCashPolicy.exclusion(line("2026-01-01", "Lançamento Financeiro", "Quitado", Map.of(StatementCashPolicy.HISTORICAL_START, "2026-01-01")), today));
    }

    @Test
    void transferenciaESaldoNaoSaoReceitaOuDespesa() {
        for (String origin : List.of("Transferência", "Saldo Conta Bancária")) {
            assertNotNull(StatementCashPolicy.exclusion(line("2026-09-01", origin, "Quitado", Map.of()), today));
    
        }}

    @Test
    void futuroFicaArquivadoAteDataESituacaoPermitirem() {
        var l = line("2026-10-31", "Lançamento Financeiro", "Em aberto", Map.of());
        assertNotNull(StatementCashPolicy.exclusion(l, today));
        assertNotNull(StatementCashPolicy.exclusion(l, today.plusDays(2)));
        assertNull(StatementCashPolicy.exclusion(line("2026-10-31", "Lançamento Financeiro", "Pago", Map.of()), today.plusDays(2)));
    }
}
