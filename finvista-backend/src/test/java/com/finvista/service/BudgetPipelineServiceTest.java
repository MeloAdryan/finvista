package com.finvista.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finvista.dto.BudgetPipelineResponse;
import com.finvista.dto.BudgetRequest;
import com.finvista.model.Budget;
import com.finvista.model.Cliente;
import com.finvista.model.FinancialTransaction;
import com.finvista.repository.BudgetRepository;
import com.finvista.repository.FinancialTransactionRepository;

class BudgetPipelineServiceTest {

    

          private static final Long CLIENTE_ID = 10L;

        private BudgetRepository budgetRepository;
        private FinancialTransactionRepository financialTransactionRepository;
        private ClienteContextService clienteContextService;
        private BudgetPipelineService budgetPipelineService;
        private Cliente clienteAtual;

        @BeforeEach
        void setUp() {

            budgetRepository
                    = mock(BudgetRepository.class);

            financialTransactionRepository
                    = mock(FinancialTransactionRepository.class);

            clienteContextService
                    = mock(ClienteContextService.class);

            clienteAtual
                    = mock(Cliente.class);

            when(clienteAtual.getId())
                    .thenReturn(CLIENTE_ID);

            when(clienteContextService.getClienteAtual())
                    .thenReturn(clienteAtual);

            when(clienteContextService.getClienteAtualId())
                    .thenReturn(CLIENTE_ID);

            budgetPipelineService
                    = new BudgetPipelineService(
                            budgetRepository,
                            financialTransactionRepository,
                            clienteContextService
                    );
        }

        @Test
        void deveCalcularOrcamentoPorCentroDeCusto() {

            LocalDate inicio
                    = LocalDate.of(2026, 1, 1);

            LocalDate fim
                    = LocalDate.of(2026, 12, 31);

            Budget budget
                    = criarBudgetComCliente(
                            "Orçamento Operacional 2026",
                            "Custo Operacional",
                            null,
                            new BigDecimal("150000.00"),
                            inicio,
                            fim,
                            CLIENTE_ID
                    );

            FinancialTransaction despesa1
                    = criarDespesa(
                            new BigDecimal("30000.00"),
                            null,
                            "Custo Operacional"
                    );

            FinancialTransaction despesa2
                    = criarDespesa(
                            new BigDecimal("40000.00"),
                            null,
                            "Custo Operacional"
                    );

            FinancialTransaction outroCentro
                    = criarDespesa(
                            new BigDecimal("50000.00"),
                            null,
                            "Despesas Administrativas"
                    );

            when(
                    budgetRepository
                            .findAllByClienteIdOrderByDataInicioDesc(
                                    CLIENTE_ID
                            )
            ).thenReturn(
                    List.of(budget)
            );

            when(
                    financialTransactionRepository
                            .findByClienteIdAndTipoAndDataBetweenOrderByDataAsc(
                                    CLIENTE_ID,
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

            List<BudgetPipelineResponse> resultado
                    = budgetPipelineService.listar();

            assertEquals(
                    1,
                    resultado.size()
            );

            BudgetPipelineResponse response
                    = resultado.get(0);

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

            LocalDate inicio
                    = LocalDate.of(2026, 1, 1);

            LocalDate fim
                    = LocalDate.of(2026, 12, 31);

            Budget budget
                    = criarBudgetComCliente(
                            "Marketing",
                            "Despesas Administrativas",
                            "Publicidade",
                            new BigDecimal("100000.00"),
                            inicio,
                            fim,
                            CLIENTE_ID
                    );

            FinancialTransaction corresponde
                    = criarDespesa(
                            new BigDecimal("25000.00"),
                            "Publicidade",
                            "Despesas Administrativas"
                    );

            FinancialTransaction categoriaDiferente
                    = criarDespesa(
                            new BigDecimal("10000.00"),
                            "Telefonia",
                            "Despesas Administrativas"
                    );

            FinancialTransaction centroDiferente
                    = criarDespesa(
                            new BigDecimal("15000.00"),
                            "Publicidade",
                            "Custo Operacional"
                    );

            when(
                    budgetRepository
                            .findAllByClienteIdOrderByDataInicioDesc(
                                    CLIENTE_ID
                            )
            ).thenReturn(
                    List.of(budget)
            );

            when(
                    financialTransactionRepository
                            .findByClienteIdAndTipoAndDataBetweenOrderByDataAsc(
                                    CLIENTE_ID,
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

            BudgetPipelineResponse resultado
                    = budgetPipelineService
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

            LocalDate inicio
                    = LocalDate.of(2026, 1, 1);

            LocalDate fim
                    = LocalDate.of(2026, 12, 31);

            Budget budget
                    = criarBudgetComCliente(
                            "Orçamento Geral",
                            null,
                            null,
                            new BigDecimal("200000.00"),
                            inicio,
                            fim,
                            CLIENTE_ID
                    );

            FinancialTransaction primeira
                    = criarDespesa(
                            new BigDecimal("30000.00"),
                            "Categoria A",
                            "Centro A"
                    );

            FinancialTransaction segunda
                    = criarDespesa(
                            new BigDecimal("20000.00"),
                            "Categoria B",
                            "Centro B"
                    );

            when(
                    budgetRepository
                            .findAllByClienteIdOrderByDataInicioDesc(
                                    CLIENTE_ID
                            )
            ).thenReturn(
                    List.of(budget)
            );

            when(
                    financialTransactionRepository
                            .findByClienteIdAndTipoAndDataBetweenOrderByDataAsc(
                                    CLIENTE_ID,
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

            BudgetPipelineResponse resultado
                    = budgetPipelineService
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

            LocalDate inicio
                    = LocalDate.of(2026, 1, 1);

            LocalDate fim
                    = LocalDate.of(2026, 12, 31);

            Budget budget
                    = criarBudgetComCliente(
                            "Orçamento TI",
                            "Tecnologia",
                            null,
                            new BigDecimal("100000.00"),
                            inicio,
                            fim,
                            CLIENTE_ID
                    );

            FinancialTransaction despesa
                    = criarDespesa(
                            new BigDecimal("115000.00"),
                            null,
                            "Tecnologia"
                    );

            when(
                    budgetRepository
                            .findAllByClienteIdOrderByDataInicioDesc(
                                    CLIENTE_ID
                            )
            ).thenReturn(
                    List.of(budget)
            );

            when(
                    financialTransactionRepository
                            .findByClienteIdAndTipoAndDataBetweenOrderByDataAsc(
                                    CLIENTE_ID,
                                    "DESPESA",
                                    inicio,
                                    fim
                            )
            ).thenReturn(
                    List.of(despesa)
            );

            BudgetPipelineResponse resultado
                    = budgetPipelineService
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

            LocalDate inicio
                    = LocalDate.of(2026, 1, 1);

            LocalDate fim
                    = LocalDate.of(2026, 12, 31);

            BudgetRequest request
                    = new BudgetRequest(
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
                    invocation
                    -> invocation.getArgument(0)
            );

            when(
                    financialTransactionRepository
                            .findByClienteIdAndTipoAndDataBetweenOrderByDataAsc(
                                    CLIENTE_ID,
                                    "DESPESA",
                                    inicio,
                                    fim
                            )
            ).thenReturn(
                    List.of()
            );

            BudgetPipelineResponse resultado
                    = budgetPipelineService
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
                    clienteContextService
            ).getClienteAtual();

            verify(
                    budgetRepository
            ).save(
                    any(Budget.class)
            );
        }

        @Test
        void deveRejeitarValorPlanejadoMenorOuIgualAZero() {

            BudgetRequest request
                    = new BudgetRequest(
                            "Orçamento",
                            null,
                            null,
                            BigDecimal.ZERO,
                            LocalDate.of(
                                    2026,
                                    1,
                                    1
                            ),
                            LocalDate.of(
                                    2026,
                                    12,
                                    31
                            )
                    );

            IllegalArgumentException exception
                    = assertThrows(
                            IllegalArgumentException.class,
                            ()
                            -> budgetPipelineService
                                    .cadastrar(request)
                    );

            assertEquals(
                    "O valor planejado deve ser maior que zero.",
                    exception.getMessage()
            );

            verify(
                    budgetRepository,
                    never()
            ).save(
                    any(Budget.class)
            );
        }

        @Test
        void deveRejeitarNomeVazio() {

            BudgetRequest request
                    = new BudgetRequest(
                            "   ",
                            null,
                            null,
                            new BigDecimal("10000.00"),
                            LocalDate.of(
                                    2026,
                                    1,
                                    1
                            ),
                            LocalDate.of(
                                    2026,
                                    12,
                                    31
                            )
                    );

            IllegalArgumentException exception
                    = assertThrows(
                            IllegalArgumentException.class,
                            ()
                            -> budgetPipelineService
                                    .cadastrar(request)
                    );

            assertEquals(
                    "O nome do orçamento é obrigatório.",
                    exception.getMessage()
            );

            verify(
                    budgetRepository,
                    never()
            ).save(
                    any(Budget.class)
            );
        }

        @Test
        void deveRejeitarPeriodoInvertido() {

            BudgetRequest request
                    = new BudgetRequest(
                            "Orçamento",
                            null,
                            null,
                            new BigDecimal("10000.00"),
                            LocalDate.of(
                                    2026,
                                    12,
                                    31
                            ),
                            LocalDate.of(
                                    2026,
                                    1,
                                    1
                            )
                    );

            IllegalArgumentException exception
                    = assertThrows(
                            IllegalArgumentException.class,
                            ()
                            -> budgetPipelineService
                                    .cadastrar(request)
                    );

            assertEquals(
                    "A data final não pode ser anterior à data inicial.",
                    exception.getMessage()
            );

            verify(
                    budgetRepository,
                    never()
            ).save(
                    any(Budget.class)
            );
        }

        @Test
        void deveRetornarListaVaziaQuandoNaoExistiremOrcamentos() {

            when(
                    budgetRepository
                            .findAllByClienteIdOrderByDataInicioDesc(
                                    CLIENTE_ID
                            )
            ).thenReturn(
                    List.of()
            );

            List<BudgetPipelineResponse> resultado
                    = budgetPipelineService.listar();

            assertEquals(
                    0,
                    resultado.size()
            );

            verify(
                    budgetRepository
            ).findAllByClienteIdOrderByDataInicioDesc(
                    CLIENTE_ID
            );
        }

        @Test
        void deveListarSomenteOrcamentosDoClienteAtual() {

            when(
                    budgetRepository
                            .findAllByClienteIdOrderByDataInicioDesc(
                                    CLIENTE_ID
                            )
            ).thenReturn(
                    List.of()
            );

            List<BudgetPipelineResponse> resultado
                    = budgetPipelineService.listar();

            assertEquals(
                    0,
                    resultado.size()
            );

            verify(
                    clienteContextService
            ).getClienteAtual();

            verify(
                    budgetRepository
            ).findAllByClienteIdOrderByDataInicioDesc(
                    CLIENTE_ID
            );

            verify(
                    budgetRepository,
                    never()
            ).findAllByOrderByDataInicioDesc();
        }

        @Test
        void devePropagarErroQuandoNaoExistirClienteAtualNoCadastro() {

            BudgetRequest request
                    = new BudgetRequest(
                            "Orçamento Administrativo",
                            null,
                            null,
                            new BigDecimal("50000.00"),
                            LocalDate.of(
                                    2026,
                                    1,
                                    1
                            ),
                            LocalDate.of(
                                    2026,
                                    12,
                                    31
                            )
                    );

            when(
                    clienteContextService.getClienteAtual()
            ).thenThrow(
                    new IllegalStateException(
                            "Nenhum cliente foi selecionado pelo administrador."
                    )
            );

            IllegalStateException exception
                    = assertThrows(
                            IllegalStateException.class,
                            ()
                            -> budgetPipelineService
                                    .cadastrar(request)
                    );

            assertEquals(
                    "Nenhum cliente foi selecionado pelo administrador.",
                    exception.getMessage()
            );

            verify(
                    budgetRepository,
                    never()
            ).save(
                    any(Budget.class)
            );
        }

        @Test
        void deveUsarClienteDoOrcamentoAoCalcularDespesas() {

            Long clienteIdA = 10L;

            LocalDate inicio
                    = LocalDate.of(2026, 1, 1);

            LocalDate fim
                    = LocalDate.of(2026, 12, 31);

            Budget budget
                    = criarBudgetComCliente(
                            "Orçamento Cliente A",
                            null,
                            null,
                            new BigDecimal("100000.00"),
                            inicio,
                            fim,
                            clienteIdA
                    );

            FinancialTransaction despesaClienteA
                    = criarDespesa(
                            new BigDecimal("25000.00"),
                            "Categoria A",
                            "Centro A"
                    );

            when(
                    budgetRepository
                            .findAllByClienteIdOrderByDataInicioDesc(
                                    clienteIdA
                            )
            ).thenReturn(
                    List.of(budget)
            );

            when(
                    financialTransactionRepository
                            .findByClienteIdAndTipoAndDataBetweenOrderByDataAsc(
                                    clienteIdA,
                                    "DESPESA",
                                    inicio,
                                    fim
                            )
            ).thenReturn(
                    List.of(despesaClienteA)
            );

            BudgetPipelineResponse resultado
                    = budgetPipelineService
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

            verify(
                    financialTransactionRepository
            ).findByClienteIdAndTipoAndDataBetweenOrderByDataAsc(
                    clienteIdA,
                    "DESPESA",
                    inicio,
                    fim
            );
        }

        @Test
        void deveRejeitarOrcamentoSemClienteAssociado() {

            LocalDate inicio
                    = LocalDate.of(2026, 1, 1);

            LocalDate fim
                    = LocalDate.of(2026, 12, 31);

            Budget budget
                    = new Budget(
                            "Orçamento sem cliente",
                            null,
                            null,
                            new BigDecimal("100000.00"),
                            inicio,
                            fim
                    );

            when(
                    budgetRepository
                            .findAllByClienteIdOrderByDataInicioDesc(
                                    CLIENTE_ID
                            )
            ).thenReturn(
                    List.of(budget)
            );

            IllegalStateException exception
                    = assertThrows(
                            IllegalStateException.class,
                            ()
                            -> budgetPipelineService.listar()
                    );

            assertEquals(
                    "O orçamento não possui um cliente associado.",
                    exception.getMessage()
            );
        }

        @Test
        void deveConsultarSomenteClienteSelecionadoSemVisaoGlobal() {

            Long clienteSelecionadoId = 20L;

            Cliente clienteSelecionado
                    = mock(Cliente.class);

            when(
                    clienteSelecionado.getId()
            ).thenReturn(
                    clienteSelecionadoId
            );

            when(
                    clienteContextService.getClienteAtual()
            ).thenReturn(
                    clienteSelecionado
            );

            when(
                    budgetRepository
                            .findAllByClienteIdOrderByDataInicioDesc(
                                    clienteSelecionadoId
                            )
            ).thenReturn(
                    List.of()
            );

            List<BudgetPipelineResponse> resultado
                    = budgetPipelineService.listar();

            assertEquals(
                    0,
                    resultado.size()
            );

            verify(
                    budgetRepository
            ).findAllByClienteIdOrderByDataInicioDesc(
                    clienteSelecionadoId
            );

            verify(
                    budgetRepository,
                    never()
            ).findAllByOrderByDataInicioDesc();
        }

        private Budget criarBudgetComCliente(
                String nome,
                String centroCusto,
                String categoria,
                BigDecimal valorPlanejado,
                LocalDate dataInicio,
                LocalDate dataFim,
                Long clienteId
        ) {

            Budget budget
                    = new Budget(
                            nome,
                            centroCusto,
                            categoria,
                            valorPlanejado,
                            dataInicio,
                            dataFim
                    );

            Cliente cliente
                    = mock(Cliente.class);

            when(
                    cliente.getId()
            ).thenReturn(
                    clienteId
            );

            budget.setCliente(cliente);

            return budget;
        }

        private FinancialTransaction criarDespesa(
                BigDecimal valor,
                String categoria,
                String centroCusto
        ) {

            return new FinancialTransaction(
                    LocalDate.of(
                            2026,
                            6,
                            15
                    ),
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

    
            

        
            

        
            
    
             
                    
                        
            

        
            

        
            
         
                 
                 
                 
                 
                         
                 
                 
                 
                 
