package com.finvista.service;

import com.finvista.dto.BudgetPipelineResponse;
import com.finvista.dto.BudgetRequest;
import com.finvista.model.Budget;
import com.finvista.model.Cliente;
import com.finvista.model.FinancialTransaction;
import com.finvista.repository.BudgetRepository;
import com.finvista.repository.FinancialTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class BudgetPipelineService {

    private static final String TIPO_DESPESA = "DESPESA";

    private final BudgetRepository budgetRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final ClienteContextService clienteContextService;
    private final ExpenseAllocationService expenseAllocationService;

    @Autowired
    public BudgetPipelineService(
            BudgetRepository budgetRepository,
            FinancialTransactionRepository financialTransactionRepository,
            ClienteContextService clienteContextService,
            ExpenseAllocationService expenseAllocationService
    ) {
        this.budgetRepository = budgetRepository;
        this.financialTransactionRepository = financialTransactionRepository;
        this.clienteContextService = clienteContextService;
        this.expenseAllocationService = expenseAllocationService;
    }

    // Compatibilidade com testes existentes que verificam dados legados sem rateios.
    // O Spring utiliza o construtor de quatro dependências acima.
    public BudgetPipelineService(
            BudgetRepository budgetRepository,
            FinancialTransactionRepository financialTransactionRepository,
            ClienteContextService clienteContextService
    ) {
        this(budgetRepository, financialTransactionRepository, clienteContextService, null);
    }

    /**
     * Lista os orçamentos pertencentes ao cliente atual.
     *
     * Usuário comum:
     * utiliza o cliente associado ao próprio usuário.
     *
     * ADMIN:
     * utiliza o cliente selecionado na sessão.
     */
    @Transactional(readOnly = true)
    public List<BudgetPipelineResponse> listar() {

        Long clienteId = obterClienteIdAtual();

        return budgetRepository
                .findAllByClienteIdOrderByDataInicioDesc(
                        clienteId
                )
                .stream()
                .map(this::criarResposta)
                .toList();
    }

    /**
     * Cadastra um orçamento para o cliente atual.
     *
     * O cliente nunca é aceito diretamente do frontend.
     * Ele é determinado pelo contexto autenticado.
     */
    @Transactional
    public BudgetPipelineResponse cadastrar(
            BudgetRequest request
    ) {

        validar(request);

        Cliente cliente =
                clienteContextService.getClienteAtual();

        Budget budget = new Budget(
                request.nome().trim(),
                normalizarOpcional(
                        request.centroCusto()
                ),
                normalizarOpcional(
                        request.categoria()
                ),
                request.valorPlanejado(),
                request.dataInicio(),
                request.dataFim()
        );

        budget.setCliente(cliente);

        Budget salvo =
                budgetRepository.save(budget);

        return criarResposta(salvo);
    }

    private BudgetPipelineResponse criarResposta(
            Budget budget
    ) {

        if (budget == null) {
            throw new IllegalStateException(
                    "O orçamento informado é inválido."
            );
        }

        if (budget.getCliente() == null
                || budget.getCliente().getId() == null) {

            throw new IllegalStateException(
                    "O orçamento não possui um cliente associado."
            );
        }

        Long clienteId =
                budget.getCliente().getId();

        List<FinancialTransaction> despesas =
                financialTransactionRepository
                        .findByClienteIdAndTipoAndDataBetweenOrderByDataAsc(
                                clienteId,
                                TIPO_DESPESA,
                                budget.getDataInicio(),
                                budget.getDataFim()
                        );

        List<ExpenseAllocationService.Parte> partes = expenseAllocationService != null
                ? expenseAllocationService.dividir(clienteId, despesas)
                : despesas.stream()
                    .map(despesa -> new ExpenseAllocationService.Parte(
                            despesa.getCategoria(), despesa.getCentroCusto(),
                            despesa.getValor() == null ? BigDecimal.ZERO : despesa.getValor()))
                    .toList();

        BigDecimal valorUtilizado = partes.stream()
                .filter(parte -> correspondeAoOrcamento(budget, parte))
                .map(ExpenseAllocationService.Parte::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal valorDisponivel =
                budget.getValorPlanejado()
                        .subtract(valorUtilizado);

        BigDecimal percentualUtilizado =
                calcularPercentualUtilizado(
                        budget.getValorPlanejado(),
                        valorUtilizado
                );

        return new BudgetPipelineResponse(
                budget.getId(),
                budget.getNome(),
                budget.getCentroCusto(),
                budget.getCategoria(),
                budget.getValorPlanejado(),
                valorUtilizado,
                valorDisponivel,
                percentualUtilizado,
                budget.getDataInicio(),
                budget.getDataFim()
        );
    }

    private boolean correspondeAoOrcamento(
            Budget budget,
            ExpenseAllocationService.Parte parte
    ) {

        boolean centroCustoCorresponde =
                normalizarOpcional(budget.getCentroCusto()) == null
                        || iguaisIgnorandoMaiusculas(
                                budget.getCentroCusto(),
                                parte.centroCusto()
                        );

        boolean categoriaCorresponde =
                normalizarOpcional(budget.getCategoria()) == null
                        || iguaisIgnorandoMaiusculas(
                                budget.getCategoria(),
                                parte.categoria()
                        );

        return centroCustoCorresponde
                && categoriaCorresponde;
    }

    private boolean iguaisIgnorandoMaiusculas(
            String primeiro,
            String segundo
    ) {

        if (primeiro == null
                || segundo == null) {

            return false;
        }

        return primeiro
                .trim()
                .equalsIgnoreCase(
                        segundo.trim()
                );
    }

    private BigDecimal calcularPercentualUtilizado(
            BigDecimal valorPlanejado,
            BigDecimal valorUtilizado
    ) {

        if (valorPlanejado == null
                || valorPlanejado.compareTo(
                        BigDecimal.ZERO
                ) == 0) {

            return BigDecimal.ZERO;
        }

        return valorUtilizado
                .multiply(
                        new BigDecimal("100")
                )
                .divide(
                        valorPlanejado,
                        2,
                        RoundingMode.HALF_UP
                );
    }

    private String normalizarOpcional(
            String valor
    ) {

        if (valor == null
                || valor.isBlank()) {

            return null;
        }

        return valor.trim();
    }

    /**
     * Resolve o cliente efetivamente utilizado pelo FinVista.
     *
     * Usuário comum -> cliente vinculado.
     * ADMIN -> cliente selecionado na sessão.
     */
    private Long obterClienteIdAtual() {

        return clienteContextService
                .getClienteAtual()
                .getId();
    }

    private void validar(
            BudgetRequest request
    ) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Os dados do orçamento são obrigatórios."
            );
        }

        if (request.nome() == null
                || request.nome().isBlank()) {

            throw new IllegalArgumentException(
                    "O nome do orçamento é obrigatório."
            );
        }

        if (request.valorPlanejado() == null
                || request.valorPlanejado()
                .compareTo(
                        BigDecimal.ZERO
                ) <= 0) {

            throw new IllegalArgumentException(
                    "O valor planejado deve ser maior que zero."
            );
        }

        if (request.dataInicio() == null) {
            throw new IllegalArgumentException(
                    "A data inicial é obrigatória."
            );
        }

        if (request.dataFim() == null) {
            throw new IllegalArgumentException(
                    "A data final é obrigatória."
            );
        }

        if (request.dataFim()
                .isBefore(
                        request.dataInicio()
                )) {

            throw new IllegalArgumentException(
                    "A data final não pode ser anterior à data inicial."
            );
        }
    }
}
