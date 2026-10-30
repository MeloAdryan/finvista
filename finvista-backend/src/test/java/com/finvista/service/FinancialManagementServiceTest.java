package com.finvista.service;

import com.finvista.model.*;
import com.finvista.repository.*;
import com.finvista.service.FinancialViewService.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialManagementServiceTest {

    final LocalDate today = LocalDate.of(2026, 10, 23);
    final Long client = 7L;
    ClienteContextService ctx = mock(ClienteContextService.class);
    FinancialTransactionRepository tx = mock(FinancialTransactionRepository.class);
    ContaAzulAutoRunRepository runs = mock(ContaAzulAutoRunRepository.class);
    ContaAzulAutoItemRepository items = mock(ContaAzulAutoItemRepository.class);
    ContaAzulAutoConfigRepository configs = mock(ContaAzulAutoConfigRepository.class);
    StatementMovementRepository statements = mock(StatementMovementRepository.class);
    FinancialViewService view = mock(FinancialViewService.class);
    FinancialManagementService service;

    @BeforeEach
    void setup() {
        when(ctx.getClienteAtualId()).thenReturn(client);
        when(tx.findByClienteId(client)).thenReturn(List.of());
        when(runs.findTop10ByClienteIdOrderByCriadoEmDesc(client)).thenReturn(List.of());
        when(runs.findFirstByClienteIdAndStatusInOrderByConcluidoEmDesc(eq(client), any())).thenReturn(Optional.empty());
        when(configs.findById(client)).thenReturn(Optional.empty());
        when(items.findTerminalByCliente(client)).thenReturn(List.of());
        when(statements.findByClienteIdOrderByIdAsc(client)).thenReturn(List.of());
        service = new FinancialManagementService(ctx, tx, runs, items, configs, statements, mock(StatementImportService.class), mock(ContaAzulApiOperationRepository.class), view, new ObjectMapper(), Clock.fixed(Instant.parse("2026-10-23T15:00:00Z"), ZoneOffset.UTC), true);
    }

    FinancialTransaction item(long id, int days, String open) {
        var t = new FinancialTransaction();
        org.springframework.test.util.ReflectionTestUtils.setField(t, "id", id);
        t.setTipo("DESPESA");
        t.setDescricao("Conta " + id);
        t.setValor(new BigDecimal("1000"));
        t.setValorAberto(new BigDecimal("400"));
        t.setValorTotalAberto(open == null ? null : new BigDecimal(open));
        t.setDataVencimento(today.minusDays(days));
        return t;
    }

    @Test
    void exactAgingBoundariesAndPartialRemainingOnly() {
        int[] days = {-1, 0, 1, 30, 31, 60, 61, 90, 91};
        var expected = List.of(FinancialOpenItems.Bucket.A_VENCER, FinancialOpenItems.Bucket.HOJE, FinancialOpenItems.Bucket.DIAS_1_30, FinancialOpenItems.Bucket.DIAS_1_30, FinancialOpenItems.Bucket.DIAS_31_60, FinancialOpenItems.Bucket.DIAS_31_60, FinancialOpenItems.Bucket.DIAS_61_90, FinancialOpenItems.Bucket.DIAS_61_90, FinancialOpenItems.Bucket.MAIS_90);
        for (int i = 0; i < days.length; i++) {
            var result = FinancialOpenItems.read(List.of(item(i + 1, days[i], "62.50")), today).get(0);
            assertEquals(expected.get(i), result.faixa());
            assertEquals(new BigDecimal("62.50"), result.aberto());
        }
    }

    @Test
    void noFallbackForUnknownBalanceAndNoDate() {
        var a = item(1, 5, null);
        var b = item(2, 5, "10");
        b.setDataVencimento(null);
        var rows = FinancialOpenItems.read(List.of(a, b), today);
        assertEquals(FinancialOpenItems.Bucket.SEM_SALDO, rows.get(0).faixa());
        assertNull(rows.get(0).aberto());
        assertEquals(FinancialOpenItems.Bucket.SEM_VENCIMENTO, rows.get(1).faixa());
    }

    @Test
    void cancelledPaidAndUnsupportedTypesAreExcluded() {
        var a = item(1, 5, "10");
        a.setSituacao("CANCELADO");
        var b = item(2, 5, "0");
        var c = item(3, 5, "20");
        c.setTipo("TRANSFERENCIA");
        assertTrue(FinancialOpenItems.read(List.of(a, b, c), today).isEmpty());
    }

    @Test
    void negativeBalanceIsUnknownNotNegativeExposure() {
        var rows = FinancialOpenItems.read(List.of(item(1, 1, "-10")), today);
        assertNull(rows.get(0).aberto());
    }

    ContaAzulAutoItem pending(String parcel, String status, long id) {
        var i = new ContaAzulAutoItem();
        i.id = UUID.randomUUID();
        i.parcelaId = parcel;
        i.tipo = "DESPESA";
        i.status = status;
        i.lancamentoId = id;
        i.concluidoEm = Instant.parse("2026-10-23T14:00:00Z");
        return i;
    }

    @Test
    void newRunWithNoTerminalResultDoesNotEraseOldPending() {
        var t = item(1, 10, "62");
        t.setValorTotalRealizado(new BigDecimal("40"));
        when(tx.findByClienteId(client)).thenReturn(List.of(t));
        when(items.findTerminalByCliente(client)).thenReturn(List.of(pending("p", "CONFERIR", 1)));
        var h = service.health();
        assertEquals(1, h.totalPendencias());
        assertEquals(new BigDecimal("102.00"), h.liquidoVinculado());
        assertTrue(h.atualizacaoAtrasada());
    }

    @Test
    void latestSuccessfulTerminalClosesOldPending() {
        when(items.findTerminalByCliente(client)).thenReturn(List.of(pending("p", "SEM_ALTERACAO", 1), pending("p", "CONFERIR", 1)));
        assertEquals(0, service.health().totalPendencias());
    }

    @Test
    void distinctPendingReferencesDoNotDoubleCountSameTransaction() {
        var t = item(1, 1, "50");
        t.setValorTotalRealizado(new BigDecimal("50"));
        when(tx.findByClienteId(client)).thenReturn(List.of(t));
        when(items.findTerminalByCliente(client)).thenReturn(List.of(pending("a", "CONFERIR", 1), pending("b", "CONFERIR", 1)));
        var h = service.health();
        assertEquals(2, h.totalPendencias());
        assertEquals(new BigDecimal("100.00"), h.liquidoVinculado());
    }

    @Test
    void healthDoesNotPretendPrincipalIsLiquid() {
        when(tx.findByClienteId(client)).thenReturn(List.of(item(1, 1, null)));
        when(items.findTerminalByCliente(client)).thenReturn(List.of(pending("a", "CONFERIR", 1)));
        var h = service.health();
        assertEquals(new BigDecimal("0.00"), h.liquidoVinculado());
        assertEquals(new BigDecimal("1000.00"), h.principalSemLiquido());
    }

    @Test
    void agingPaginationAndTypeAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> service.aging("OUTRO", null, 0, 20));
        assertThrows(IllegalArgumentException.class, () -> service.aging(null, null, -1, 20));
        assertThrows(IllegalArgumentException.class, () -> service.aging(null, null, 0, 51));
        assertThrows(IllegalArgumentException.class, () -> service.aging(null, "OUTRO", 0, 20));
    }

    @Test
    void agingFilteredSummaryAndRecordsUseOnlySelectedCompany() {
        var a = item(1, 31, "25");
        var b = item(2, 0, "50");
        when(tx.findByClienteId(client)).thenReturn(List.of(a, b));
        var data = service.aging("DESPESA", "DIAS_31_60", 0, 20);
        assertEquals(1, data.total());
        assertEquals(1L, data.itens().get(0).id());
        assertEquals(new BigDecimal("25.00"), data.grupos().stream().filter(g -> g.tipo().equals("DESPESA") && g.faixa() == FinancialOpenItems.Bucket.DIAS_31_60).findFirst().orElseThrow().valorConhecido());
        verify(tx).findByClienteId(client);
        verifyNoMoreInteractions(tx);
    }

    @Test
    void changedCompanyStopsResponse() {
        when(ctx.getClienteAtualId()).thenReturn(7L, 8L);
        assertThrows(IllegalArgumentException.class, () -> service.aging(null, null, 0, 20));
    }

    @Test
    void foreignDetailCannotBeOpenedEvenInPositionMode() {
        assertThrows(IllegalArgumentException.class, () -> service.detail(88L, null, null, true));
        verify(tx).findByClienteId(client);
    }

    @Test
    void settledRecordCannotBeOpenedAsOpenExposure() {
        when(tx.findByClienteId(client)).thenReturn(List.of(item(1, 5, "0")));
        assertThrows(IllegalArgumentException.class, () -> service.detail(1L, null, null, true));
    }

    @Test
    void positionDetailPreservesUnknownOpenAndDoesNotInventRateios() {
        when(tx.findByClienteId(client)).thenReturn(List.of(item(1, 5, null)));
        var d = service.detail(1L, null, null, true);
        assertNull(d.valorSelecionado());
        assertTrue(d.rateios().isEmpty());
        assertTrue(d.baixas().isEmpty());
    }

    @Test
    void forecastRejectsInvalidPeriodAndCompetence() {
        assertThrows(IllegalArgumentException.class, () -> service.forecast(null, today, Criterio.BAIXA));
        assertThrows(IllegalArgumentException.class, () -> service.forecast(today, today.minusDays(1), Criterio.BAIXA));
        assertThrows(IllegalArgumentException.class, () -> service.forecast(today, today, Criterio.COMPETENCIA));
    }

    Resposta actual(List<Linha> lines) {
        return new Resposta(client, null, new Faixa(today.minusDays(1), today.plusDays(1)), today, new Total(new BigDecimal("10"), new BigDecimal("40"), new BigDecimal("-30")), List.of(), lines, Map.of(), List.of(), List.of(), List.of(), List.of(), "LIQUIDO", "");
    }

    @Test
    void forecastSeparatesRealPaymentsFutureRemainderAndOverdueRemainder() {
        var old = item(1, 1, "60");
        var future = item(2, -1, "70");
        var todayItem = item(3, 0, "80");
        todayItem.setTipo("RECEITA");
        when(tx.findByClienteId(client)).thenReturn(List.of(old, future, todayItem));
        when(view.consultar(any())).thenReturn(actual(List.of(new Linha(4L, "p", "Pago", "DESPESA", today, today, today, "Serviços", "Geral", new BigDecimal("40")))));
        var d = service.forecast(today.minusDays(1), today.plusDays(1), Criterio.BAIXA);
        assertEquals(new BigDecimal("40"), d.realizado().despesa());
        assertEquals(new BigDecimal("70.00"), d.compromissosFuturos().despesa());
        assertEquals(new BigDecimal("80.00"), d.compromissosFuturos().receita());
        assertEquals(new BigDecimal("60.00"), d.vencidosAtuais().despesa());
        assertEquals(new BigDecimal("40.00"), d.meses().get(0).pago());
    }

    @Test
    void forecastExcludesUnknownAndOutOfPeriodOpenValues() {
        var outside = item(1, 40, "100");
        var unknown = item(2, 0, null);
        when(tx.findByClienteId(client)).thenReturn(List.of(outside, unknown));
        when(view.consultar(any())).thenReturn(actual(List.of()));
        var d = service.forecast(today, today, Criterio.BAIXA_EXTRATO);
        assertTrue(d.compromissos().isEmpty());
        assertEquals(1, d.exclusoes().get("Compromissos sem saldo líquido ou vencimento"));
        assertEquals(new BigDecimal("0.00"), d.compromissosFuturos().despesa());
    }

    @Test
    void detailRejectsUnselectedMovementEvenForExistingParcel() {
        when(view.consultar(any())).thenReturn(actual(List.of(new Linha(1L, "p", "Conta", "DESPESA", today, today, today, "A", "Geral", new BigDecimal("40"), null, null, null, null, null, "b1"))));
        assertThrows(IllegalArgumentException.class, () -> service.detail(1L, "b2", new Filtro(Criterio.BAIXA, Periodo.PERSONALIZADO, Situacao.TODAS, null, today, today, null, null), false));
    }

    @Test
    void detailSumsSelectedAllocationsOnly() {
        var t = item(1, 0, "60");
        when(tx.findByClienteId(client)).thenReturn(List.of(t));
        when(view.consultar(any())).thenReturn(actual(List.of(new Linha(1L, "p", "Conta", "DESPESA", today, today, today, "A", "Geral", new BigDecimal("25"), null, null, null, null, null, "b1"), new Linha(1L, "p", "Conta", "DESPESA", today, today, today, "B", "Geral", new BigDecimal("15"), null, null, null, null, null, "b1"))));
        var d = service.detail(1L, "b1", new Filtro(Criterio.BAIXA, Periodo.PERSONALIZADO, Situacao.TODAS, null, today, today, null, null), false);
        assertEquals(new BigDecimal("40.00"), d.valorSelecionado());
        assertEquals(2, d.rateios().size());
        assertTrue(d.baixas().isEmpty());
    }

    @Test
    void exportacaoCompletaNaoSeLimitaAPaginaEFechaComFaixasEPainel() {
        var rows = new ArrayList<FinancialTransaction>();
        for (int i = 1; i <= 65; i++) {
            rows.add(item(i, i % 3 - 1, "10"));
        }
        var unknown = item(66, 1, null);
        rows.add(unknown);
        var noDate = item(67, 0, "20");
        noDate.setDataVencimento(null);
        rows.add(noDate);
        when(tx.findByClienteId(client)).thenReturn(rows);
        var export = service.openPosition();
        assertEquals(67, export.itens().size());
        assertEquals(today, export.referencia());
        assertEquals(67, export.grupos().stream().mapToLong(FinancialManagementService.AgingGroup::quantidade).sum());
        assertEquals(0, new BigDecimal("670").compareTo(export.grupos().stream().map(FinancialManagementService.AgingGroup::valorConhecido).reduce(BigDecimal.ZERO, BigDecimal::add)));
        var pos = new FinancialPositionService(tx, ctx).consultar(today);
        var due = export.grupos().stream().filter(g -> g.faixa() == FinancialOpenItems.Bucket.HOJE).map(FinancialManagementService.AgingGroup::valorConhecido).reduce(BigDecimal.ZERO, BigDecimal::add);
        var future = export.grupos().stream().filter(g -> g.faixa() == FinancialOpenItems.Bucket.A_VENCER).map(FinancialManagementService.AgingGroup::valorConhecido).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, due.compareTo(pos.pagar().venceHoje()));
        assertEquals(0, future.compareTo(pos.pagar().aVencerAposHoje()));
        assertEquals(0, due.add(future).compareTo(pos.pagar().aVencer()));
        assertEquals(1, pos.semValorAberto());
        assertEquals(1, pos.semVencimento());
        assertEquals(20, service.aging("DESPESA", null, 0, 20).itens().size());
        verify(tx, never()).save(any());
    }

    @Test
    void exportacaoCompletaExigeContextoEstavel() {
        when(ctx.getClienteAtualId()).thenReturn(client, 8L);
        assertThrows(IllegalArgumentException.class, () -> service.openPosition());
    }
}
