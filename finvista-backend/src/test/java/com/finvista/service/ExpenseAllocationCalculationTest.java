package com.finvista.service;

import com.finvista.dto.*;
import com.finvista.model.*;
import com.finvista.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExpenseAllocationCalculationTest {
    private static final Long CLIENTE = 7L, ID = 2516L;
    private static final LocalDate INICIO = LocalDate.of(2026, 4, 1), FIM = LocalDate.of(2026, 4, 30);
    private FinancialAllocationRepository rates;
    private FinancialTransactionRepository transactions;
    private ClienteContextService context;
    private ExpenseAllocationService allocations;
    private FinancialTransaction financiamento;
    private List<FinancialAllocation> parcelas;

    @BeforeEach
    void preparar() {
        rates = mock(FinancialAllocationRepository.class);
        transactions = mock(FinancialTransactionRepository.class);
        context = mock(ClienteContextService.class);
        when(context.getClienteAtualId()).thenReturn(CLIENTE);
        allocations = new ExpenseAllocationService(rates);
        financiamento = despesa(ID, "5530.75", "Juros", "Financeiras");
        parcelas = List.of(rateio(1, "Juros", "-114.61", "Financeiras", "-114.61"),
                rateio(2, "Móveis", "-5416.14", "Administrativas", "-5416.14"));
        carregar(parcelas);
        when(transactions.findByClienteIdAndTipoOrderByDataDesc(CLIENTE, "DESPESA"))
                .thenReturn(List.of(financiamento));
        when(transactions.findByClienteIdAndDataBetweenOrderByDataAsc(CLIENTE, INICIO, FIM))
                .thenReturn(List.of(financiamento));
        when(transactions.findAllByClienteIdOrderByDataDesc(CLIENTE)).thenReturn(List.of(financiamento));
        when(transactions.findByClienteIdAndTipoAndDataBetweenOrderByDataAsc(CLIENTE, "DESPESA", INICIO, FIM))
                .thenReturn(List.of(financiamento));
    }

    @Test
    void divideFinanciamentoSemSomarNovamenteOPai() {
        var parts = allocations.dividir(CLIENTE, List.of(financiamento));
        assertEquals(2, parts.size());
        dinheiro("114.61", parts.get(0).valor());
        dinheiro("5416.14", parts.get(1).valor());
        dinheiro("5530.75", soma(parts));
        verify(rates).findByLancamentoClienteIdAndLancamentoIdInOrderByLancamentoIdAscBlocoAsc(CLIENTE, List.of(ID));
        verify(rates, never()).save(any());
    }

    @Test
    void preservaCompensacaoEntreParcelasSemAplicarAbsIndividual() {
        financiamento.setValor(new BigDecimal("90.00"));
        carregar(List.of(rateio(1, "Despesa", "-100", "Financeiras", "-100"),
                rateio(2, "Reversão", "10", "Financeiras", "10")));
        var parts = allocations.dividir(CLIENTE, List.of(financiamento));
        dinheiro("100", parts.get(0).valor());
        dinheiro("-10", parts.get(1).valor());
        dinheiro("90", soma(parts));
    }

    @Test
    void aceitaRateiosComSinalPositivoEValoresComEscalaDiferente() {
        carregar(List.of(rateio(1, "Juros", "114.6100", "Financeiras", "114.61"),
                rateio(2, "Móveis", "5416.14", "Administrativas", "5416.140")));
        var parts = allocations.dividir(CLIENTE, List.of(financiamento));
        assertEquals("Financeiras", parts.get(0).centroCusto());
        dinheiro("5530.75", soma(parts));
    }

    @Test
    void somaLegadoERateiosUmaVezCada() {
        FinancialTransaction legado = despesa(99L, "20", "Material", "Operacional");
        when(rates.findByLancamentoClienteIdAndLancamentoIdInOrderByLancamentoIdAscBlocoAsc(CLIENTE, List.of(ID, 99L)))
                .thenReturn(parcelas);
        var parts = allocations.dividir(CLIENTE, List.of(financiamento, legado));
        assertEquals(3, parts.size());
        dinheiro("5550.75", soma(parts));
    }

    @Test
    void semIdOuSemDespesasNaoConsultaRateios() {
        assertTrue(allocations.dividir(CLIENTE, List.of()).isEmpty());
        var parts = allocations.dividir(CLIENTE, List.of(despesa(null, "12", null, null)));
        assertEquals("Sem categoria", parts.get(0).categoria());
        assertEquals("Sem centro de custo", parts.get(0).centroCusto());
        dinheiro("12", soma(parts));
        verifyNoInteractions(rates);
    }

    @Test
    void rateioDeOutraDespesaNaoEntraNoTotal() {
        var outra = despesa(999L, "5530.75", "Outra", "Outro");
        carregar(List.of(new FinancialAllocation(outra, 1, "Outra", new BigDecimal("-5530.75"),
                "Outro", new BigDecimal("-5530.75"))));
        var parts = allocations.dividir(CLIENTE, List.of(financiamento));
        assertEquals("Juros", parts.get(0).categoria());
        dinheiro("5530.75", soma(parts));
    }

    @Test
    void somaInconsistenteVaiParaRevisaoSemPerderTotal() {
        carregar(List.of(rateio(1, "Juros", "-114.61", "Financeiras", "-114.61")));
        var parts = allocations.dividir(CLIENTE, List.of(financiamento));
        assertEquals(1, parts.size());
        assertEquals("Categoria a revisar", parts.get(0).categoria());
        assertEquals("Centro a revisar", parts.get(0).centroCusto());
        dinheiro("5530.75", soma(parts));
    }

    @Test
    void blocoRepetidoNaoEhContadoComoRateioValido() {
        carregar(List.of(rateio(1, "Juros", "-114.61", "Financeiras", "-114.61"),
                rateio(1, "Móveis", "-5416.14", "Administrativas", "-5416.14")));
        var parts = allocations.dividir(CLIENTE, List.of(financiamento));
        assertEquals("Categoria a revisar", parts.get(0).categoria());
        dinheiro("5530.75", soma(parts));
    }

    @Test
    void centroInconsistenteNaoRecebeValorInventado() {
        carregar(List.of(rateio(1, "Juros", "-114.61", "Financeiras", "-200"), parcelas.get(1)));
        var parts = allocations.dividir(CLIENTE, List.of(financiamento));
        assertEquals("Juros", parts.get(0).categoria());
        assertEquals("Centro a revisar", parts.get(0).centroCusto());
        dinheiro("114.61", parts.get(0).valor());
        dinheiro("5530.75", soma(parts));
    }

    @Test
    void centroComValorAusenteOuSemNomePermaneceExplicito() {
        carregar(List.of(rateio(1, "Juros", "-114.61", "Financeiras", null),
                rateio(2, "Móveis", "-5416.14", null, null)));
        var parts = allocations.dividir(CLIENTE, List.of(financiamento));
        assertEquals("Centro a revisar", parts.get(0).centroCusto());
        assertEquals("Sem centro de custo", parts.get(1).centroCusto());
        dinheiro("5530.75", soma(parts));
    }

    @Test
    void despesasPorCategoriaECentrosUsamSegundaParcelaNoHistoricoENoMes() {
        var expenses = new ExpenseDistributionService(transactions, context, allocations);
        var centers = new CostCenterService(transactions, context, allocations);
        for (var resultado : List.of(expenses.listar(), expenses.listarNoMes(YearMonth.of(2026, 4)))) {
            dinheiro("5416.14", resultado.stream().filter(r -> r.categoria().equals("Móveis")).findFirst().orElseThrow().valor());
            dinheiro("114.61", resultado.stream().filter(r -> r.categoria().equals("Juros")).findFirst().orElseThrow().valor());
        }
        for (var resultado : List.of(centers.listar(), centers.listarNoMes(YearMonth.of(2026, 4)))) {
            dinheiro("5416.14", resultado.stream().filter(r -> r.nome().equals("Administrativas")).findFirst().orElseThrow().valor());
            dinheiro("5530.75", resultado.stream().map(CostCenterResponse::valor).reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        assertEquals(List.of("Móveis"), expenses.listarCategoriasPorCentroCusto(" administrativas "));
    }

    @Test
    void mensalNaoIncluiDespesasForaDoPeriodoOuReceitas() {
        var fora = despesa(99L, "999", "Outra", "Outro"); fora.setData(INICIO.minusDays(1));
        var receita = despesa(100L, "999", "Outra", "Outro"); receita.setTipo("RECEITA");
        when(transactions.findByClienteIdAndDataBetweenOrderByDataAsc(CLIENTE, INICIO, FIM))
                .thenReturn(List.of(financiamento, fora, receita));
        var expenses = new ExpenseDistributionService(transactions, context, allocations);
        dinheiro("5530.75", expenses.listarNoMes(YearMonth.of(2026, 4)).stream()
                .map(ExpenseDistributionResponse::valor).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void classificacaoEFiltroUsamCategoriaDoSegundoRateio() {
        var classifications = mock(CostCategoryClassificationRepository.class);
        var c = new CostCategoryClassification(); c.setCategoriaChave("móveis");
        c.setComportamento(CostBehavior.FIXO); c.setNatureza(CostNature.MATERIAIS);
        when(classifications.findByClienteId(CLIENTE)).thenReturn(List.of(c));
        var service = new CostCenterAnalysisService(transactions, classifications, context,
                mock(FinancialReferenceService.class), allocations);
        dinheiro("5416.14", service.analisar(INICIO, FIM, CostBehavior.FIXO, CostNature.MATERIAIS).total());
        dinheiro("5530.75", service.analisar(INICIO, FIM, null, null).total());
        assertEquals(List.of("Juros", "Móveis"), service.listarClassificacoes().stream()
                .map(CostClassificationDto::categoria).toList());
        assertEquals("Móveis", service.salvar(new CostClassificationDto("Móveis", CostBehavior.FIXO, CostNature.MATERIAIS)).categoria());
        verify(classifications).save(any());
    }

    @Test
    void metaGeralMantemTotalEStatusAoFiltrarParcela() {
        var result = metas(null, null).buscarSituacao(1L, "Administrativas", "Móveis");
        dinheiro("5530.75", result.gastoAtual());
        dinheiro("5416.14", result.gastoFiltrado());
        dinheiro("4469.25", result.saldoMeta());
        assertEquals("NORMAL", result.status());
    }

    @Test
    void metaSalvaPorCategoriaSomaSomenteParcelaECombinaFiltros() {
        var service = metas(null, "Juros");
        dinheiro("114.61", service.buscarSituacao(1L).gastoAtual());
        dinheiro("0", service.buscarSituacao(1L, null, "Móveis").gastoFiltrado());
        dinheiro("114.61", metas("Financeiras", null).buscarSituacao(1L).gastoAtual());
    }

    private SpendingGoalService metas(String centro, String categoria) {
        var repository = mock(SpendingGoalRepository.class);
        var cliente = mock(Cliente.class); when(cliente.getId()).thenReturn(CLIENTE);
        var meta = new SpendingGoal(); meta.setCliente(cliente); meta.setTipo("MENSAL");
        meta.setDataInicio(INICIO); meta.setDataFim(FIM); meta.setValorLimite(new BigDecimal("10000"));
        meta.setPercentualAlerta(70); meta.setCentroCusto(centro); meta.setCategoria(categoria);
        when(repository.findByIdAndClienteId(1L, CLIENTE)).thenReturn(Optional.of(meta));
        return new SpendingGoalService(repository, transactions, new FinancialCalculationService(), context, allocations);
    }

    private FinancialTransaction despesa(Long id, String valor, String categoria, String centro) {
        var t = spy(new FinancialTransaction(LocalDate.of(2026, 4, 16), "Financiamento", "DESPESA",
                new BigDecimal(valor), categoria, centro, "CONTA_AZUL", null));
        when(t.getId()).thenReturn(id); return t;
    }
    private FinancialAllocation rateio(int bloco, String cat, String valor, String centro, String valorCentro) {
        return new FinancialAllocation(financiamento, bloco, cat, new BigDecimal(valor), centro,
                valorCentro == null ? null : new BigDecimal(valorCentro));
    }
    private void carregar(List<FinancialAllocation> valores) {
        when(rates.findByLancamentoClienteIdAndLancamentoIdInOrderByLancamentoIdAscBlocoAsc(CLIENTE, List.of(ID)))
                .thenReturn(valores);
    }
    private BigDecimal soma(List<ExpenseAllocationService.Parte> partes) {
        return partes.stream().map(ExpenseAllocationService.Parte::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private void dinheiro(String esperado, BigDecimal valor) {
        assertEquals(0, new BigDecimal(esperado).compareTo(valor), "Valor esperado: " + esperado + "; obtido: " + valor);
    }
}
