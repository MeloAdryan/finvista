package com.finvista.service;

import com.finvista.dto.SpendingGoalRecurrenceRequest;
import com.finvista.model.Cliente;
import com.finvista.model.FinancialTransaction;
import com.finvista.model.SpendingGoal;
import com.finvista.repository.FinancialTransactionRepository;
import com.finvista.repository.SpendingGoalRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpendingGoalRecurrenceCalculationTest {

    @Test
    void cadaMesTemConsumoSaldoEStatusPropriosSemTransferencia() {
        var repository = mock(SpendingGoalRepository.class);
        var transactions = mock(FinancialTransactionRepository.class);
        var context = mock(ClienteContextService.class);
        var cliente = mock(Cliente.class);
        when(cliente.getId()).thenReturn(7L);
        when(context.isAdmin()).thenReturn(true);
        when(context.getClienteAtual()).thenReturn(cliente);
        when(context.getClienteAtualId()).thenReturn(7L);
        List<SpendingGoal> salvas = new ArrayList<>();
        when(repository.saveAll(anyList())).thenAnswer(invocation -> {
            List<SpendingGoal> metas = invocation.getArgument(0);
            for (int i = 0; i < metas.size(); i++) {
                ReflectionTestUtils.setField(metas.get(i), "id", (long) i + 1);
            }
            salvas.addAll(metas);
            return metas;
        });
        new SpendingGoalRecurrenceService(repository, context).criar(new SpendingGoalRecurrenceRequest(
                "Operações", "MENSAL", LocalDate.parse("2026-01-01"), LocalDate.parse("2026-02-28"),
                new BigDecimal("1000"), 80, null, null));
        for (var meta : salvas) {
            when(repository.findByIdAndClienteId(meta.getId(), 7L)).thenReturn(Optional.of(meta));
            var gasto = new FinancialTransaction();
            gasto.setTipo("DESPESA");
            gasto.setData(meta.getDataInicio());
            gasto.setValor(new BigDecimal(meta.getDataInicio().getMonthValue() == 1 ? "900" : "50"));
            when(transactions.findByClienteIdAndTipoAndDataBetweenOrderByDataAsc(7L, "DESPESA", meta.getDataInicio(), meta.getDataFim()))
                    .thenReturn(List.of(gasto));
        }
        var service = new SpendingGoalService(repository, transactions, new FinancialCalculationService(), context);
        var janeiro = service.buscarSituacao(1L);
        var fevereiro = service.buscarSituacao(2L);
        assertEquals(new BigDecimal("900"), janeiro.gastoAtual());
        assertEquals(new BigDecimal("50"), fevereiro.gastoAtual());
        assertEquals("ALERTA", janeiro.status());
        assertEquals("NORMAL", fevereiro.status());
        assertEquals(new BigDecimal("100.00"), janeiro.saldoMeta());
        assertEquals(new BigDecimal("950.00"), fevereiro.saldoMeta());
        assertEquals(new BigDecimal("1000.00"), fevereiro.valorLimite());
        assertEquals(janeiro.serie().id(), fevereiro.serie().id());
        assertEquals("Operações", fevereiro.serie().nome());
    }

    @Test
    void graficoDiarioAceitaVigenciaPersonalizadaMaiorQueUmAno() {
        var goals = mock(SpendingGoalService.class);
        var transactions = mock(FinancialTransactionRepository.class);
        var allocations = mock(ExpenseAllocationService.class);
        var cliente = mock(Cliente.class);
        when(cliente.getId()).thenReturn(7L);
        var meta = new SpendingGoal("SEM_RECORRENCIA", LocalDate.parse("2026-01-01"),
                LocalDate.parse("2027-12-31"), new BigDecimal("5000"), 80);
        meta.setCliente(cliente);
        when(goals.buscarPorId(1L)).thenReturn(meta);
        when(transactions.findByClienteIdAndTipoAndDataBetweenOrderByDataAsc(7L, "DESPESA", meta.getDataInicio(), meta.getDataFim()))
                .thenReturn(List.of());
        when(allocations.dividirDetalhado(7L, List.of())).thenReturn(List.of());
        var service = new SpendingGoalTimelineService(goals, transactions, allocations,
                Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC));
        var timeline = service.consultar(1L);
        assertEquals(730, timeline.diasTotais());
        assertEquals(730, timeline.pontos().size());
        assertEquals(new BigDecimal("5000.00"), timeline.pontos().get(729).ritmoIdeal());
    }
}
