package com.finvista.service;

import com.finvista.dto.BudgetPipelineResponse;
import com.finvista.dto.BudgetRequest;
import com.finvista.model.Budget;
import com.finvista.model.FinancialTransaction;
import com.finvista.repository.BudgetRepository;
import com.finvista.repository.FinancialTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BudgetPipelineServiceTest {

    private BudgetRepository budgetRepository;
    private FinancialTransactionRepository financialTransactionRepository;
    private BudgetPipelineService budgetPipelineService;

    @BeforeEach
    void setUp() {
        budgetRepository =
                mock(BudgetRepository.class);

        financialTransactionRepository =
                mock(FinancialTransactionRepository.class);

        budgetPipelineService =
                new BudgetPipelineService(
                        budgetRepository,
                        financialTransactionRepository
                );
    }

    @Test
    void deveCalcularOrcamentoPorCentroDeCusto() {
        LocalDate inicio =
                LocalDate.of(2026, 1, 1);

        LocalDate fim =
                LocalDate.of(2026, 12, 31);

        Budget budget =
                new Budget(
                        "Orçamento Operacional 2026",
                        "Custo Operacional",
                        null,
                        new BigDecimal("150000.00"),
                        inicio,
                        fim
                );

        FinancialTransaction despesa1 =
                criarDespesa(
                        new BigDecimal("30000.00"),
                        null,
                        "Custo Operacional"
                );

        FinancialTransaction despesa2 =
                criarDespesa(
                        new BigDecimal("40000.00"),
                        null,
                        "Custo Operacional"
                );

        FinancialTransaction outroCentro =
                criarDespesa(
                        new BigDecimal("50000.00"),
                        null,
                        "Despesas Administrativas"
                );

        when(
                budgetRepository
                        .findAllByOrderByDataInicioDesc()
        ).thenReturn(
                List.of(budget)
        );

        when(
                financialTransactionRepository
                        .findByTipoAndDataBetweenOrderByDataAsc(
                                "DESPESA",
                                inicio,
                                fim
                        )
        ).thenReturn(
                List.of(
                        despesa1,
                        despesa2,
                        outroCentro
                )
        );

        List<BudgetPipelineResponse> resultado =
                budgetPipelineService.listar();

        assertEquals(
                1,
                resultado.size()
        );

        BudgetPipelineResponse response =
                resultado.get(0);

        assertEquals(
                "Orçamento Operacional 2026",
                response.nome()
        );

        assertEquals(
                new BigDecimal("150000.00"),
                response.valorPlanejado()
        );

        assertEquals(
                new BigDecimal("70000.00"),
                response.valorUtilizado()
        );

        assertEquals(
                new BigDecimal("80000.00"),
                response.valorDisponivel()
        );

        assertEquals(
                new BigDecimal("46.67"),
                response.percentualUtilizado()
        );
    }

    @Test
    void deveFiltrarPorCentroDeCustoECategoria() {
        LocalDate inicio =
                LocalDate.of(2026, 1, 1);

        LocalDate fim =
                LocalDate.of(2026, 12, 31);

        Budget budget =
                new Budget(
                        "Marketing",
                        "Despesas Administrativas",
                        "Publicidade",
                        new BigDecimal("100000.00"),
                        inicio,
                        fim
                );

        FinancialTransaction corresponde =
                criarDespesa(
                        new BigDecimal("25000.00"),
                        "Publicidade",
                        "Despesas Administrativas"
                );

        FinancialTransaction categoriaDiferente =
                criarDespesa(
                        new BigDecimal("10000.00"),
                        "Telefonia",
                        "Despesas Administrativas"
                );

        FinancialTransaction centroDiferente =
                criarDespesa(
                        new BigDecimal("15000.00"),
                        "Publicidade",
                        "Custo Operacional"
                );

        when(
                budgetRepository
                        .findAllByOrderByDataInicioDesc()
        ).thenReturn(
                List.of(budget)
        );

        when(
                financialTransactionRepository
                        .findByTipoAndDataBetweenOrderByDataAsc(
                                "DESPESA",
                                inicio,
                                fim
                        )
        ).thenReturn(
                List.of(
                        corresponde,
                        categoriaDiferente,
                        centroDiferente
                )
        );

        BudgetPipelineResponse resultado =
                budgetPipelineService
                        .listar()
                        .get(0);

        assertEquals(
                new BigDecimal("25000.00"),
                resultado.valorUtilizado()
        );

        assertEquals(
                new BigDecimal("75000.00"),
                resultado.valorDisponivel()
        );

        assertEquals(
                new BigDecimal("25.00"),
                resultado.percentualUtilizado()
        );
    }

    @Test
    void devePermitirOrcamentoGeralSemCentroOuCategoria() {
        LocalDate inicio =
                LocalDate.of(2026, 1, 1);

        LocalDate fim =
                LocalDate.of(2026, 12, 31);

        Budget budget =
                new Budget(
                        "Orçamento Geral",
                        null,
                        null,
                        new BigDecimal("200000.00"),
                        inicio,
                        fim
                );

        FinancialTransaction primeira =
                criarDespesa(
                        new BigDecimal("30000.00"),
                        "Categoria A",
                        "Centro A"
                );

        FinancialTransaction segunda =
                criarDespesa(
                        new BigDecimal("20000.00"),
                        "Categoria B",
                        "Centro B"
                );

        when(
                budgetRepository
                        .findAllByOrderByDataInicioDesc()
        ).thenReturn(
                List.of(budget)
        );

        when(
                financialTransactionRepository
                        .findByTipoAndDataBetweenOrderByDataAsc(
                                "DESPESA",
                                inicio,
                                fim
                        )
        ).thenReturn(
                List.of(
                        primeira,
                        segunda
                )
        );

        BudgetPipelineResponse resultado =
                budgetPipelineService
                        .listar()
                        .get(0);

        assertEquals(
                new BigDecimal("50000.00"),
                resultado.valorUtilizado()
        );

        assertEquals(
                new BigDecimal("150000.00"),
                resultado.valorDisponivel()
        );

        assertEquals(
                new BigDecimal("25.00"),
                resultado.percentualUtilizado()
        );
    }

    @Test
    void devePermitirOrcamentoUltrapassarCemPorCento() {
        LocalDate inicio =
                LocalDate.of(2026, 1, 1);

        LocalDate fim =
                LocalDate.of(2026, 12, 31);

        Budget budget =
                new Budget(
                        "Orçamento TI",
                        "Tecnologia",
                        null,
                        new BigDecimal("100000.00"),
                        inicio,
                        fim
                );

        FinancialTransaction despesa =
                criarDespesa(
                        new BigDecimal("115000.00"),
                        null,
                        "Tecnologia"
                );

        when(
                budgetRepository
                        .findAllByOrderByDataInicioDesc()
        ).thenReturn(
                List.of(budget)
        );

        when(
                financialTransactionRepository
                        .findByTipoAndDataBetweenOrderByDataAsc(
                                "DESPESA",
                                inicio,
                                fim
                        )
        ).thenReturn(
                List.of(despesa)
        );

        BudgetPipelineResponse resultado =
                budgetPipelineService
                        .listar()
                        .get(0);

        assertEquals(
                new BigDecimal("115000.00"),
                resultado.valorUtilizado()
        );

        assertEquals(
                new BigDecimal("-15000.00"),
                resultado.valorDisponivel()
        );

        assertEquals(
                new BigDecimal("115.00"),
                resultado.percentualUtilizado()
        );
    }

    @Test
    void deveCadastrarOrcamentoFinanceiro() {
        LocalDate inicio =
                LocalDate.of(2026, 1, 1);

        LocalDate fim =
                LocalDate.of(2026, 12, 31);

        BudgetRequest request =
                new BudgetRequest(
                        "  Orçamento Administrativo  ",
                        "  Despesas Administrativas  ",
                        null,
                        new BigDecimal("50000.00"),
                        inicio,
                        fim
                );

        when(
                budgetRepository.save(
                        any(Budget.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        when(
                financialTransactionRepository
                        .findByTipoAndDataBetweenOrderByDataAsc(
                                "DESPESA",
                                inicio,
                                fim
                        )
        ).thenReturn(
                List.of()
        );

        BudgetPipelineResponse resultado =
                budgetPipelineService
                        .cadastrar(request);

        assertEquals(
                "Orçamento Administrativo",
                resultado.nome()
        );

        assertEquals(
                "Despesas Administrativas",
                resultado.centroCusto()
        );

        assertEquals(
                new BigDecimal("50000.00"),
                resultado.valorPlanejado()
        );

        assertEquals(
        new BigDecimal("0.00"),
        resultado.percentualUtilizado()
);
        assertEquals(
                new BigDecimal("50000.00"),
                resultado.valorDisponivel()
        );


        verify(
                budgetRepository
        ).save(
                any(Budget.class)
        );
    }

    @Test
    void deveRejeitarValorPlanejadoMenorOuIgualAZero() {
        BudgetRequest request =
                new BudgetRequest(
                        "Orçamento",
                        null,
                        null,
                        BigDecimal.ZERO,
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 12, 31)
                );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                budgetPipelineService
                                        .cadastrar(request)
                );

        assertEquals(
                "O valor planejado deve ser maior que zero.",
                exception.getMessage()
        );
    }

    @Test
    void deveRejeitarNomeVazio() {
        BudgetRequest request =
                new BudgetRequest(
                        "   ",
                        null,
                        null,
                        new BigDecimal("10000.00"),
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 12, 31)
                );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                budgetPipelineService
                                        .cadastrar(request)
                );

        assertEquals(
                "O nome do orçamento é obrigatório.",
                exception.getMessage()
        );
    }

    @Test
    void deveRejeitarPeriodoInvertido() {
        BudgetRequest request =
                new BudgetRequest(
                        "Orçamento",
                        null,
                        null,
                        new BigDecimal("10000.00"),
                        LocalDate.of(2026, 12, 31),
                        LocalDate.of(2026, 1, 1)
                );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                budgetPipelineService
                                        .cadastrar(request)
                );

        assertEquals(
                "A data final não pode ser anterior à data inicial.",
                exception.getMessage()
        );
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoExistiremOrcamentos() {
        when(
                budgetRepository
                        .findAllByOrderByDataInicioDesc()
        ).thenReturn(
                List.of()
        );

        List<BudgetPipelineResponse> resultado =
                budgetPipelineService.listar();

        assertEquals(
                0,
                resultado.size()
        );
    }

    private FinancialTransaction criarDespesa(
            BigDecimal valor,
            String categoria,
            String centroCusto
    ) {
        return new FinancialTransaction(
                LocalDate.of(2026, 6, 15),
                "Despesa de teste",
                "DESPESA",
                valor,
                categoria,
                centroCusto,
                "TESTE",
                null
        );
    }
}