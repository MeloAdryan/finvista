package com.finvista.service;

import com.finvista.dto.ExpenseDistributionResponse;
import com.finvista.model.FinancialTransaction;
import com.finvista.repository.FinancialTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ExpenseDistributionService {

    private static final String TIPO_DESPESA = "DESPESA";

    private final FinancialTransactionRepository financialTransactionRepository;
    private final ClienteContextService clienteContextService;

    private ExpenseAllocationService expenseAllocationService;

    // O Spring usa este construtor para incluir os rateios nos cálculos.
    @Autowired
    public ExpenseDistributionService(
            FinancialTransactionRepository repository,
            ClienteContextService context,
            ExpenseAllocationService allocations
    ) {
        this(repository, context);
        this.expenseAllocationService = Objects.requireNonNull(allocations);
    }

    // Mantém a compatibilidade com os testes anteriores.
    public ExpenseDistributionService(
            FinancialTransactionRepository financialTransactionRepository,
            ClienteContextService clienteContextService
    ) {
        this.financialTransactionRepository = financialTransactionRepository;
        this.clienteContextService = clienteContextService;
    }

    public List<ExpenseDistributionResponse> listar() {
        Long clienteId = obterClienteIdAtual();

        List<FinancialTransaction> despesas = financialTransactionRepository
                .findByClienteIdAndTipoOrderByDataDesc(
                        clienteId,
                        TIPO_DESPESA
                );

        return agruparPorCategoria(partes(clienteId, despesas));
    }

    public List<ExpenseDistributionResponse> listarNoMes(YearMonth mes) {
        if (mes == null) {
            throw new IllegalArgumentException(
                    "Mês de análise é obrigatório."
            );
        }

        Long clienteId = obterClienteIdAtual();

        List<FinancialTransaction> despesas = financialTransactionRepository
                .findByClienteIdAndDataBetweenOrderByDataAsc(
                        clienteId,
                        mes.atDay(1),
                        mes.atEndOfMonth()
                )
                .stream()
                .filter(lancamento ->
                        lancamento.getData() != null
                        && mes.equals(YearMonth.from(lancamento.getData()))
                        && lancamento.getValor() != null
                        && lancamento.getTipo() != null
                        && TIPO_DESPESA.equalsIgnoreCase(
                                lancamento.getTipo().trim()
                        )
                )
                .toList();

        return agruparPorCategoria(partes(clienteId, despesas));
    }

    public List<String> listarCategoriasPorCentroCusto(String centroCusto) {
        if (centroCusto == null || centroCusto.isBlank()) {
            return List.of();
        }

        Long clienteId = obterClienteIdAtual();

        List<FinancialTransaction> despesas = financialTransactionRepository
                .findByClienteIdAndTipoOrderByDataDesc(
                        clienteId,
                        TIPO_DESPESA
                );

        // Cada parcela tem sua própria categoria e seu próprio centro.
        return partes(clienteId, despesas)
                .stream()
                .filter(parte ->
                        parte.centroCusto().equalsIgnoreCase(
                                centroCusto.trim()
                        )
                )
                .map(ExpenseAllocationService.Parte::categoria)
                .filter(categoria ->
                        !ExpenseAllocationService.SEM_CATEGORIA.equals(categoria)
                        && !ExpenseAllocationService.CATEGORIA_REVISAR.equals(
                                categoria
                        )
                )
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private List<ExpenseDistributionResponse> agruparPorCategoria(
            List<ExpenseAllocationService.Parte> parcelas
    ) {
        Map<String, BigDecimal> totais = parcelas
                .stream()
                .collect(Collectors.groupingBy(
                        ExpenseAllocationService.Parte::categoria,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                ExpenseAllocationService.Parte::valor,
                                BigDecimal::add
                        )
                ));

        return totais.entrySet()
                .stream()
                .sorted(
                        Map.Entry.<String, BigDecimal>comparingByValue()
                                .reversed()
                                .thenComparing(Map.Entry::getKey)
                )
                .map(entrada -> new ExpenseDistributionResponse(
                        entrada.getKey(),
                        entrada.getValue()
                ))
                .toList();
    }

    private List<ExpenseAllocationService.Parte> partes(
            Long clienteId,
            List<FinancialTransaction> despesas
    ) {
        return expenseAllocationService == null
                ? ExpenseAllocationService.legado(despesas)
                : expenseAllocationService.dividir(clienteId, despesas);
    }

    private Long obterClienteIdAtual() {
        return clienteContextService.getClienteAtualId();
    }
}