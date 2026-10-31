package com.finvista.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvista.model.*;
import com.finvista.repository.*;
import com.finvista.service.FinancialViewService.*;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatementImportServiceTest {

    StatementMovementRepository repo = mock(StatementMovementRepository.class);
    ClienteRepository clients = mock(ClienteRepository.class);
    ClienteContextService context = mock(ClienteContextService.class);
    ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    Clock clock = Clock.fixed(Instant.parse("2026-10-22T15:00:00Z"), ZoneOffset.UTC);
    List<StatementMovement> saved = new ArrayList<>();
    StatementImportService importer;
    Cliente client = new Cliente("Empresa de teste");

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(client, "id", 7L);
        when(context.getClienteAtualId()).thenReturn(7L);
        when(context.getClienteAtual()).thenReturn(client);
        when(clients.bloquearAtivoPorId(7L)).thenReturn(Optional.of(client));
        when(repo.findByClienteIdOrderByIdAsc(7L)).thenAnswer(inv -> new ArrayList<>(saved));
        when(repo.saveAllAndFlush(anyList())).thenAnswer(inv -> {
            List<StatementMovement> rows = inv.getArgument(0);
            for (var row : rows) {
                ReflectionTestUtils.setField(row, "id", (long) saved.size() + 1);
                saved.add(row);
            }
            return rows;
        });
        importer = new StatementImportService(new ContaAzulStatementService(), repo, clients, context, json, clock);
    }

    static MockMultipartFile file(String name, String... rows) throws Exception {
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet();
            var h = sheet.createRow(0);
            String[] names = {"Data movimento", "Descrição", "Tipo", "Origem do lançamento", "Valor (R$)", "Data de competência", "Data original de vencimento", "Categoria 1", "Valor na Categoria 1", "Centro de Custo 1", "Valor no Centro de Custo 1", "Situação", "Conta bancária", "Saldo conta (R$)"};
            for (int i = 0; i < names.length; i++) {
                h.createCell(i).setCellValue(names[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                String[] custom = rows[r].split(";", -1);
                String amount = custom[0], desc = custom.length > 1 ? custom[1] : "Pagamento", origin = custom.length > 2 ? custom[2] : "Lançamento Financeiro";
                String type = custom.length > 3 ? custom[3] : "Despesa", category = custom.length > 4 ? custom[4] : "Serviços";
                String[] values = {"10/09/2026", desc, type, origin, amount, "01/08/2026", "31/08/2026", category, amount, "Geral", amount, "Conciliado", "Conta Corrente", name};
                var row = sheet.createRow(r + 1);
                for (int i = 0; i < values.length; i++) {
                    row.createCell(i).setCellValue(values[i]);
                }
            }
            book.write(out);
            return new MockMultipartFile("arquivo", name, "application/octet-stream", out.toByteArray());
        }
    }

    @Test
    void importAndRepeatAreIdempotentAndPreserveIdenticalOccurrences() throws Exception {
        var file = file("one.xls", "-10,00", "-10,00");
        var p = importer.plan(file);
        assertEquals(2, p.novas());
        assertEquals(2, importer.apply(file, p).incluidas());
        var repeated = importer.plan(file);
        assertEquals(0, repeated.novas());
        assertEquals(2, repeated.existentes());
        assertEquals(0, importer.apply(file, repeated).incluidas());
        assertEquals(2, saved.size());
    }

    @Test
    void overlappingExportsIgnoreFilenameRowOrderAndRunningBalance() throws Exception {
        var small = file("small.xls", "-10,00;A", "-20,00;B");
        importer.apply(small, importer.plan(small));
        var large = file("large.xls", "-20,00;B", "-30,00;C", "-10,00;A");
        var p = importer.plan(large);
        assertEquals(1, p.novas());
        assertEquals(2, p.existentes());
        importer.apply(large, p);
        assertEquals(3, saved.size());
    }

    @Test
    void changedValuesRequireReviewInsteadOfDoubleCounting() throws Exception {
        var initial = file("one.xls", "-10,00");
        importer.apply(initial, importer.plan(initial));
        var changed = file("changed.xls", "-11,00");
        var p = importer.plan(changed);
        assertFalse(p.conflitos().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> importer.apply(changed, p));
        assertEquals(1, saved.size());
    }

    @Test
    void changedAllocationsRequireReview() throws Exception {
        var initial = file("one.xls", "-10,00");
        importer.apply(initial, importer.plan(initial));
        var changed = file("changed.xls", "-10,00;Pagamento;Lançamento Financeiro;Despesa;Outra categoria");
        assertFalse(importer.plan(changed).conflitos().isEmpty());
    }

    @Test
    void changedFileCannotUseEarlierConfirmation() throws Exception {
        var file = file("one.xls", "-10,00");
        var p = importer.plan(file);
        var changed = file("two.xls", "-20,00");
        assertThrows(IllegalArgumentException.class, () -> importer.apply(changed, p));
        assertTrue(saved.isEmpty());
    }

    @Test
    void concurrentImportInvalidatesStalePreview() throws Exception {
        var a = file("a.xls", "-10,00;A");
        var b = file("b.xls", "-20,00;B");
        var stale = importer.plan(a);
        importer.apply(b, importer.plan(b));
        assertThrows(IllegalArgumentException.class, () -> importer.apply(a, stale));
        assertEquals(1, saved.size());
    }

    @Test
    void companyCannotChangeBetweenPreviewAndWrite() throws Exception {
        var file = file("one.xls", "-10,00");
        var p = importer.plan(file);
        when(context.getClienteAtualId()).thenReturn(8L);
        assertThrows(IllegalArgumentException.class, () -> importer.apply(file, p));
        verify(repo, never()).saveAllAndFlush(anyList());
    }

    @Test
    void invalidRowsBlockEntireFile() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> importer.plan(file("one.xls", "-10,00", "inválido")));
        verify(repo, never()).saveAllAndFlush(anyList());
    }

    @Test
    void historicalReviewPreservesMovementAndCanBeRevisedWithoutDuplicates() throws Exception {
        var input = file("historico.xls", "-189,20");
        var user = new Usuario("Contador", "contador@example.test", "hash", "ADMIN");
        ReflectionTestUtils.setField(user, "id", 99L);
        when(context.getUsuarioAutenticado()).thenReturn(user);
        importer.apply(input, importer.plan(input));
        var plan = importer.plan(input, LocalDate.of(2026, 10, 1));
        assertEquals(0, plan.novas());
        assertEquals(1, plan.revisadas());
        assertFalse(plan.avisos().isEmpty());
        importer.apply(input, plan);
        assertEquals(1, saved.size());
        var reviewed = importer.decode(saved.get(0));
        assertEquals(LocalDate.of(2026, 9, 10), reviewed.movimento());
        assertNotNull(StatementCashPolicy.exclusion(reviewed, LocalDate.of(2026, 10, 22)));
        assertEquals("99", reviewed.campos().get("finvista:revisao historica por"));
        // Reimport without an explicit decision preserves the stored review.
        importer.apply(input, importer.plan(input));
        assertNotNull(StatementCashPolicy.exclusion(importer.decode(saved.get(0)), LocalDate.of(2026, 10, 22)));
        importer.apply(input, importer.plan(input, LocalDate.of(2016, 1, 1)));
        assertNull(StatementCashPolicy.exclusion(importer.decode(saved.get(0)), LocalDate.of(2026, 10, 22)));
        assertEquals(1, saved.size());
    }

    @Test
    void historicalStartCannotBeInFutureOrOutsideSupportedDates() throws Exception {
        var input = file("historico.xls", "-189,20");
        assertThrows(IllegalArgumentException.class, () -> importer.plan(input, LocalDate.of(2027, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> importer.plan(input, LocalDate.of(1899, 1, 1)));
        verify(repo, never()).saveAllAndFlush(anyList());
    }

    @Test
    void ledgerReadRejectsCompanyChangedInAnotherTab() {
        when(context.getClienteAtualId()).thenReturn(8L);
        assertThrows(IllegalArgumentException.class, () -> importer.stored(7L));
        verify(repo, never()).findByClienteIdOrderByIdAsc(anyLong());
    }

    @Test
    void extractAnalysisUsesMovementDateAndDoesNotReadParcelOrApiSources() throws Exception {
        var file = file("one.xls", "-100,25;Pagamento", "-50,00;Transferir;Transferência", "200,00;Receber;Lançamento Financeiro;Receita");
        importer.apply(file, importer.plan(file));
        assertEquals(3, saved.size());
        var transactions = mock(FinancialTransactionRepository.class);
        var operations = mock(ContaAzulApiOperationRepository.class);
        var allocations = mock(ExpenseAllocationService.class);
        var goals = mock(SpendingGoalService.class);
        var budgets = mock(BudgetPipelineService.class);
        when(goals.listarComSituacao()).thenReturn(List.of());
        when(budgets.listar()).thenReturn(List.of());
        var view = new FinancialViewService(transactions, operations, context, allocations, goals, budgets, json, clock, repo, importer);
        var filter = new Filtro(Criterio.BAIXA_EXTRATO, Periodo.PERSONALIZADO, Situacao.TODAS, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, null);
        var result = view.consultar(filter);
        assertEquals(0, new BigDecimal("100.25").compareTo(result.totais().despesa()));
        assertEquals(0, new BigDecimal("200.00").compareTo(result.totais().receita()));
        assertEquals(2, result.linhas().size());
        assertEquals("Serviços", result.linhas().get(0).categoria());
        assertEquals("Geral", result.linhas().get(0).centro());
        assertEquals(1, result.exclusoes().get("Transferências e outras origens, preservadas fora dos indicadores"));
        verifyNoInteractions(transactions, operations, allocations);
        var august = new Filtro(Criterio.BAIXA_EXTRATO, Periodo.PERSONALIZADO, Situacao.TODAS, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), null, null);
        assertEquals(0, view.consultar(august).linhas().size());
    }

    @Test
    void installmentAllocationIsNotMistakenForAPartialPaymentAllocation() throws Exception {
        var input = file("partial.xls", "-40,00");
        byte[] bytes;
        try (var book = org.apache.poi.ss.usermodel.WorkbookFactory.create(input.getInputStream()); var out = new ByteArrayOutputStream()) {
            book.getSheetAt(0).getRow(1).getCell(8).setCellValue("-100,00");
            book.write(out);
            bytes = out.toByteArray();
        }
        var partial = new MockMultipartFile("arquivo", "partial.xls", "", bytes);
        importer.apply(partial, importer.plan(partial));
        var goals = mock(SpendingGoalService.class);
        var budgets = mock(BudgetPipelineService.class);
        when(goals.listarComSituacao()).thenReturn(List.of());
        when(budgets.listar()).thenReturn(List.of());
        var view = new FinancialViewService(mock(FinancialTransactionRepository.class), mock(ContaAzulApiOperationRepository.class), context,
                mock(ExpenseAllocationService.class), goals, budgets, json, clock, repo, importer);
        var data = view.consultar(new Filtro(Criterio.BAIXA_EXTRATO, Periodo.TODOS, Situacao.TODAS, LocalDate.of(2026, 9, 1), null, null, null, null));
        assertEquals(1, data.linhas().size());
        assertEquals("Categoria a revisar", data.linhas().get(0).categoria());
        assertEquals(0, new BigDecimal("40.00").compareTo(data.totais().despesa()));
    }
}
