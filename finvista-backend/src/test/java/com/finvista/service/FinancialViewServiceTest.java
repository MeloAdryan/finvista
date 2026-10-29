package com.finvista.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvista.model.*;
import com.finvista.integration.contaazul.ContaAzulParcelDetail;
import com.finvista.repository.*;
import com.finvista.service.FinancialViewService.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialViewServiceTest {

    @Test
    void originalAndLiquidReceiptComponentsRemainAvailableToOriginalCharts() {
        var t = item();
        t.setTipo("RECEITA");
        t.setData(LocalDate.of(2026, 9, 1));
        t.setDataCompetencia(t.getData());
        t.setValor(new BigDecimal("200.00"));
        t.setValorRealizado(new BigDecimal("150.00"));
        t.setValorAberto(new BigDecimal("50.00"));
        t.setValorTotalRealizado(new BigDecimal("155.00"));
        t.setValorTotalAberto(new BigDecimal("55.00"));
        when(transactions.findByClienteId(7L)).thenReturn(List.of(t));
        var competence = service.consultar(filter(Criterio.COMPETENCIA, Periodo.PERSONALIZADO)).linhas().get(0);
        assertEquals(0, new BigDecimal("150").compareTo(competence.principalRealizado()));
        assertEquals(0, new BigDecimal("50").compareTo(competence.principalAberto()));
        var due = service.consultar(filter(Criterio.VENCIMENTO, Periodo.PERSONALIZADO));
        assertEquals(0, new BigDecimal("210").compareTo(due.totais().receita()));
        assertEquals(0, new BigDecimal("155").compareTo(due.linhas().get(0).liquidoRealizado()));
        assertEquals(0, new BigDecimal("55").compareTo(due.linhas().get(0).liquidoAberto()));
    }

    FinancialTransactionRepository transactions = mock(FinancialTransactionRepository.class);
    ContaAzulApiOperationRepository operations = mock(ContaAzulApiOperationRepository.class);
    ClienteContextService context = mock(ClienteContextService.class);
    ExpenseAllocationService allocations = mock(ExpenseAllocationService.class);
    SpendingGoalService goals = mock(SpendingGoalService.class);
    BudgetPipelineService budgets = mock(BudgetPipelineService.class);
    FinancialViewService service;
    LocalDate today = LocalDate.of(2026, 10, 22);

    @BeforeEach
    void setup() {
        when(context.getClienteAtualId()).thenReturn(7L);
        when(goals.listarComSituacao()).thenReturn(List.of());
        when(budgets.listar()).thenReturn(List.of());
        service = new FinancialViewService(transactions, operations, context, allocations, goals, budgets, new ObjectMapper().findAndRegisterModules(), Clock.fixed(Instant.parse("2026-10-22T15:00:00Z"), ZoneOffset.UTC), mock(StatementMovementRepository.class), mock(StatementImportService.class));
    }

    Filtro filter(Criterio c, Periodo p) {
        return new Filtro(c, p, Situacao.TODAS, today, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, null);
    }

    FinancialTransaction item() {
        var t = new FinancialTransaction();
        org.springframework.test.util.ReflectionTestUtils.setField(t, "id", 1L);
        t.setTipo("DESPESA");
        t.setDescricao("Conta");
        t.setData(LocalDate.of(2026, 8, 1));
        t.setDataCompetencia(t.getData());
        t.setDataVencimento(LocalDate.of(2026, 9, 10));
        t.setValor(new BigDecimal("100"));
        t.setValorTotalRealizado(new BigDecimal("40"));
        t.setValorTotalAberto(new BigDecimal("62"));
        when(transactions.findByClienteId(7L)).thenReturn(List.of(t));
        when(allocations.dividirDetalhado(7L, List.of(t))).thenReturn(List.of(new ExpenseAllocationService.ParteDetalhada(t, new ExpenseAllocationService.Parte("Serviços", "Geral", t.getValor()))));
        return t;
    }

    @Test
    void weeklyStartsMonday() {
        var f = FinancialViewService.intervalo(filter(Criterio.COMPETENCIA, Periodo.SEMANAL), today, List.of());
        assertEquals(LocalDate.of(2026, 10, 19), f.inicio());
        assertEquals(LocalDate.of(2026, 10, 25), f.fim());
    }

    @Test
    void leapMonth() {
        var f = new Filtro(Criterio.COMPETENCIA, Periodo.MENSAL, Situacao.TODAS, LocalDate.of(2028, 2, 10), null, null, null, null);
        assertEquals(LocalDate.of(2028, 2, 29), FinancialViewService.intervalo(f, today, List.of()).fim());
    }

    @Test
    void annualAndAllIncludeFutureYears() {
        var f = FinancialViewService.intervalo(filter(Criterio.VENCIMENTO, Periodo.TODOS), today, List.of(LocalDate.of(2016, 4, 17), LocalDate.of(2029, 3, 16)));
        assertEquals(LocalDate.of(2029, 3, 16), f.fim());
        assertEquals(LocalDate.of(2016, 4, 17), f.inicio());
        assertEquals(LocalDate.of(2026, 12, 31), FinancialViewService.intervalo(filter(Criterio.VENCIMENTO, Periodo.ANUAL), today, List.of()).fim());
    }

    @Test
    void invalidCustomRejected() {
        var f = new Filtro(Criterio.COMPETENCIA, Periodo.PERSONALIZADO, Situacao.TODAS, today, today, today.minusDays(1), null, null);
        assertThrows(IllegalArgumentException.class, () -> FinancialViewService.intervalo(f, today, List.of()));
    }

    @Test
    void roundingConservesEachCent() {
        var parts = FinancialViewService.distribuir(new BigDecimal("0.01"), new BigDecimal("3"), List.of(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));
        assertEquals(new BigDecimal("0.01"), parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void competenceDoesNotBecomeDueDate() {
        item();
        var r = service.consultar(filter(Criterio.COMPETENCIA, Periodo.PERSONALIZADO));
        assertEquals(0, r.totais().despesa().signum());
    }

    @Test
    void dueUsesLiquidAndSeparatesAdjustments() {
        item();
        var r = service.consultar(filter(Criterio.VENCIMENTO, Periodo.PERSONALIZADO));
        assertEquals(0, new BigDecimal("102").compareTo(r.totais().despesa()));
        assertEquals(2, r.linhas().size());
        assertTrue(r.linhas().stream().anyMatch(l -> l.categoria().startsWith("Ajustes financeiros")));
        verify(transactions).findByClienteId(7L);
        verify(transactions, never()).save(any());
    }

    @Test
    void missingLiquidIsExcludedNotEstimated() {
        var t = item();
        t.setValorTotalAberto(null);
        var r = service.consultar(filter(Criterio.VENCIMENTO, Periodo.PERSONALIZADO));
        assertEquals(0, r.totais().despesa().signum());
        assertEquals(1, r.exclusoes().get("Sem líquido pago e aberto informado"));
    }

    @Test
    void paidAndOverdueAreDifferent() {
        var t = item();
        assertFalse(FinancialViewService.correspondeSituacao(t, Situacao.PAGAS, today));
        assertTrue(FinancialViewService.correspondeSituacao(t, Situacao.VENCIDAS, today));
        assertTrue(FinancialViewService.correspondeSituacao(t, Situacao.PARCIAIS, today));
    }

    @Test
    void dueTodayIsNotOverdue() {
        var t = item();
        t.setDataVencimento(today);
        assertFalse(FinancialViewService.correspondeSituacao(t, Situacao.VENCIDAS, today));
    }

    @Test
    void missingSnapshotDoesNotUseLastPaymentDate() {
        var t = item();
        t.setOrigem("CONTA_AZUL_API");
        t.setDataRealizacao(LocalDate.of(2026, 9, 1));
        when(operations.snapshotsAtuais(7L)).thenReturn(List.of());
        var r = service.consultar(filter(Criterio.BAIXA, Periodo.PERSONALIZADO));
        assertTrue(r.linhas().isEmpty());
        assertEquals(1, r.exclusoes().get("Sem baixas auditadas utilizáveis"));
    }

    @Test
    void cashRejectsOpenStatus() {
        var f = new Filtro(Criterio.BAIXA, Periodo.MENSAL, Situacao.VENCIDAS, today, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.consultar(f));
        verifyNoInteractions(transactions);
    }

    @Test
    void cancelledExcluded() {
        var t = item();
        t.setSituacao("CANCELADO");
        var r = service.consultar(filter(Criterio.VENCIMENTO, Periodo.PERSONALIZADO));
        assertTrue(r.linhas().isEmpty());
        assertEquals(1, r.exclusoes().get("Cancelados"));
    }

    @Test
    void companyChangeAbortsResponse() {
        item();
        when(context.getClienteAtualId()).thenReturn(7L, 8L);
        assertThrows(IllegalStateException.class, () -> service.consultar(filter(Criterio.VENCIMENTO, Periodo.PERSONALIZADO)));
    }

    ContaAzulParcelDetail.Composicao composition(String principal, String interest) {
        var p = new BigDecimal(principal);
        var j = new BigDecimal(interest);
        return new ContaAzulParcelDetail.Composicao(p, p.add(j), j, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    void snapshotWithTwoPayments(boolean duplicate) throws Exception {
        snapshotWithTwoPayments(duplicate, "DESPESA");
    }

    void snapshotWithTwoPayments(boolean duplicate, String tipo) throws Exception {
        var t = item();
        t.setTipo(tipo);
        t.setOrigem("CONTA_AZUL_API");
        t.setDocumentoReferencia("parcela");
        t.setSituacao("QUITADO");
        t.setValorRealizado(new BigDecimal("100"));
        t.setValorTotalRealizado(new BigDecimal("102"));
        t.setValorTotalAberto(BigDecimal.ZERO);
        var first = new ContaAzulParcelDetail.Baixa("first", "parcela", LocalDate.of(2026, 9, 10), composition("40", "0"));
        var second = new ContaAzulParcelDetail.Baixa("second", "parcela", LocalDate.of(2026, 10, 10), composition("60", "2"));
        var source = new ContaAzulParcelDetail("parcela", "Conta", "QUITADO", LocalDate.of(2027, 1, 5), new BigDecimal("102"), new BigDecimal("102"), BigDecimal.ZERO, composition("100", "2"), new ContaAzulParcelDetail.Evento("evento", tipo, t.getDataCompetencia(), new ContaAzulParcelDetail.Condicao(1), List.of()), null, null, duplicate ? List.of(first, first) : List.of(first, second));
        var op = new ContaAzulApiOperation(UUID.randomUUID(), new Cliente("Teste"), 3L, "parcela", "INCLUIR", 1L, Instant.now(), null, "{}", new ObjectMapper().findAndRegisterModules().writeValueAsString(source));
        when(operations.snapshotsAtuais(7L)).thenReturn(List.of(op));
    }

    @Test
    void cashUsesEachPaymentDate() throws Exception {
        snapshotWithTwoPayments(false);
        var r = service.consultar(filter(Criterio.BAIXA, Periodo.PERSONALIZADO));
        assertEquals(0, new BigDecimal("40").compareTo(r.totais().despesa()));
        assertTrue(r.exclusoes().isEmpty());
        assertTrue(r.linhas().stream().allMatch(l -> l.data().getMonthValue() == 9));
    }

    @Test
    void duplicatedPaymentsExcludeWholeParcel() throws Exception {
        snapshotWithTwoPayments(true);
        var r = service.consultar(filter(Criterio.BAIXA, Periodo.PERSONALIZADO));
        assertTrue(r.linhas().isEmpty());
        assertEquals(1, r.exclusoes().get("Sem baixas auditadas utilizáveis"));
    }

    @Test
    void annualCashAddsPaymentsAndInterestOnce() throws Exception {
        snapshotWithTwoPayments(false);
        var r = service.consultar(filter(Criterio.BAIXA, Periodo.ANUAL));
        assertEquals(0, new BigDecimal("102").compareTo(r.totais().despesa()));
        assertEquals(3, r.linhas().size());
        assertEquals(0, new BigDecimal("2").compareTo(r.linhas().stream().filter(l -> l.categoria().startsWith("Ajustes")).findFirst().orElseThrow().valor()));
    }

    @Test
    void budgetLimitIsNotProratedAndUsesIntersection() {
        item();
        var b = mock(com.finvista.dto.BudgetPipelineResponse.class);
        when(b.id()).thenReturn(8L);
        when(b.nome()).thenReturn("Setembro");
        when(b.dataInicio()).thenReturn(LocalDate.of(2026, 9, 1));
        when(b.dataFim()).thenReturn(LocalDate.of(2026, 9, 30));
        when(b.valorPlanejado()).thenReturn(new BigDecimal("1000"));
        when(budgets.listar()).thenReturn(List.of(b));
        var f = new Filtro(Criterio.VENCIMENTO, Periodo.PERSONALIZADO, Situacao.TODAS, today, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15), null, null);
        var r = service.consultar(f);
        assertEquals(new BigDecimal("1000"), r.orcamentos().get(0).planejado());
        assertTrue(r.orcamentos().get(0).recorteParcial());
        assertEquals(0, new BigDecimal("102").compareTo(r.orcamentos().get(0).consumoPeriodo()));
        assertEquals(new BigDecimal("10.20"), r.orcamentos().get(0).percentual());
    }

    @Test
    void categoryFilterKeepsFinancialAdjustmentsSeparate() {
        item();
        var f = new Filtro(Criterio.VENCIMENTO, Periodo.PERSONALIZADO, Situacao.TODAS, today, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "Serviços", null);
        var r = service.consultar(f);
        assertEquals(0, new BigDecimal("100").compareTo(r.totais().despesa()));
        assertEquals(2, r.categorias().size());
    }

    @Test
    void emptyAllRangeUsesReferenceWithoutInventingRecords() {
        when(transactions.findByClienteId(7L)).thenReturn(List.of());
        when(allocations.dividirDetalhado(7L, List.of())).thenReturn(List.of());
        var r = service.consultar(filter(Criterio.COMPETENCIA, Periodo.TODOS));
        assertEquals(today, r.intervalo().inicio());
        assertTrue(r.linhas().isEmpty());
        assertEquals(0, r.totais().receita().signum());
    }

    @Test
    void recebimentosParciaisUsamCadaDataMesmoComVencimentoEmOutroAno() throws Exception {
        snapshotWithTwoPayments(false, "RECEITA");
        var setembro = service.consultar(filter(Criterio.BAIXA, Periodo.PERSONALIZADO));
        assertEquals(0, new BigDecimal("40").compareTo(setembro.totais().receita()));
        assertEquals(0, setembro.totais().despesa().signum());
        var outubro = service.consultar(new Filtro(Criterio.BAIXA, Periodo.PERSONALIZADO, Situacao.TODAS, today, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null, null));
        assertEquals(0, new BigDecimal("62").compareTo(outubro.totais().receita()));
        var ano = service.consultar(filter(Criterio.BAIXA, Periodo.ANUAL));
        assertEquals(0, new BigDecimal("102").compareTo(ano.totais().receita()));
    }

    @Test
    void baixaFuturaNaoExcluiPagamentoHistoricoDaMesmaParcela() throws Exception {
        snapshotWithTwoPayments(false, "RECEITA");
        var mapper = new ObjectMapper().findAndRegisterModules();
        var node = mapper.readTree(operations.snapshotsAtuais(7L).get(0).getOrigemSnapshot());
        ((com.fasterxml.jackson.databind.node.ObjectNode) node.path("baixas").get(1)).put("data_pagamento", "2026-11-10");
        var op = new ContaAzulApiOperation(UUID.randomUUID(), new Cliente("Teste"), 3L, "parcela", "INCLUIR", 1L, Instant.now(), null, "{}", mapper.writeValueAsString(node));
        when(operations.snapshotsAtuais(7L)).thenReturn(List.of(op));
        var september = service.consultar(filter(Criterio.BAIXA, Periodo.PERSONALIZADO));
        assertEquals(0, new BigDecimal("40").compareTo(september.totais().receita()));
        assertTrue(september.exclusoes().isEmpty());
        var november = service.consultar(new Filtro(Criterio.BAIXA, Periodo.PERSONALIZADO, Situacao.TODAS, today,
            LocalDate.of(2026,11,1), LocalDate.of(2026,11,30), null, null));
        assertEquals(0, november.totais().receita().signum());
    }

    @Test
    void historicoDeBaixasAntesDe2000RespeitaFaixaSuportada() throws Exception {
        snapshotWithTwoPayments(false, "RECEITA");
        var t = transactions.findByClienteId(7L).get(0);
        t.setDataCompetencia(LocalDate.of(1999, 8, 1));
        var mapper = new ObjectMapper().findAndRegisterModules();
        var node = mapper.readTree(operations.snapshotsAtuais(7L).get(0).getOrigemSnapshot());
        ((com.fasterxml.jackson.databind.node.ObjectNode) node.path("evento")).put("data_competencia", "1999-08-01");
        ((com.fasterxml.jackson.databind.node.ObjectNode) node.path("baixas").get(0)).put("data_pagamento", "1999-09-10");
        ((com.fasterxml.jackson.databind.node.ObjectNode) node.path("baixas").get(1)).put("data_pagamento", "1999-10-10");
        var op = new ContaAzulApiOperation(UUID.randomUUID(), new Cliente("Teste"), 3L, "parcela", "INCLUIR", 1L, Instant.now(), null, "{}", mapper.writeValueAsString(node));
        when(operations.snapshotsAtuais(7L)).thenReturn(List.of(op));
        var r = service.consultar(new Filtro(Criterio.BAIXA, Periodo.PERSONALIZADO, Situacao.TODAS, today, LocalDate.of(1999, 9, 1), LocalDate.of(1999, 9, 30), null, null));
        assertEquals(0, new BigDecimal("40").compareTo(r.totais().receita()));
        assertTrue(r.exclusoes().isEmpty());
    }
}
