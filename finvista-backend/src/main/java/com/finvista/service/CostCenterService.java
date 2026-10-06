package com.finvista.service;

import com.finvista.dto.CostCenterResponse;
import com.finvista.model.FinancialTransaction;
import com.finvista.repository.FinancialTransactionRepository;
import java.time.YearMonth;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CostCenterService {

    private static final String TIPO_DESPESA = "DESPESA";

    private final FinancialTransactionRepository financialTransactionRepository;
    private final ClienteContextService clienteContextService;
    private ExpenseAllocationService expenseAllocationService;

    @Autowired
    public CostCenterService(FinancialTransactionRepository repository,
            ClienteContextService context, ExpenseAllocationService allocations) {
        this(repository, context);
        this.expenseAllocationService = java.util.Objects.requireNonNull(allocations);
    }

    public CostCenterService(
            FinancialTransactionRepository financialTransactionRepository,
            ClienteContextService clienteContextService
    ) {
        this.financialTransactionRepository
                = financialTransactionRepository;

        this.clienteContextService
                = clienteContextService;
    }

    public List<CostCenterResponse> listar() {
        Long clienteId = obterClienteIdAtual();
        List<FinancialTransaction> despesas = financialTransactionRepository
                .findByClienteIdAndTipoOrderByDataDesc(clienteId, TIPO_DESPESA);
        Map<String, BigDecimal> totais = partes(clienteId, despesas).stream()
                .collect(Collectors.groupingBy(ExpenseAllocationService.Parte::centroCusto,
                        Collectors.reducing(BigDecimal.ZERO, ExpenseAllocationService.Parte::valor, BigDecimal::add)));
        return totais.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .map(e -> new CostCenterResponse(e.getKey(), e.getValue())).toList();
    }

    private Long obterClienteIdAtual() {
        return clienteContextService.getClienteAtualId();
    }

    private BigDecimal obterValor(
            FinancialTransaction lancamento
    ) {

        if (lancamento.getValor() == null) {
            return BigDecimal.ZERO;
        }

        return lancamento.getValor();
    }

    public List<CostCenterResponse> listarNoMes(YearMonth mes) {
        if (mes == null) {
            throw new IllegalArgumentException("Mês de análise é obrigatório.");
        }
        Long clienteId = obterClienteIdAtual();
        List<FinancialTransaction> despesas = financialTransactionRepository
                .findByClienteIdAndDataBetweenOrderByDataAsc(clienteId, mes.atDay(1), mes.atEndOfMonth())
                .stream().filter(t -> t.getData() != null && mes.equals(YearMonth.from(t.getData()))
                && t.getValor() != null && t.getTipo() != null
                && TIPO_DESPESA.equalsIgnoreCase(t.getTipo().trim())).toList();
        Map<String, BigDecimal> totais = partes(clienteId, despesas).stream()
                .collect(Collectors.groupingBy(ExpenseAllocationService.Parte::centroCusto,
                        Collectors.reducing(BigDecimal.ZERO, ExpenseAllocationService.Parte::valor, BigDecimal::add)));
        return totais.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .map(e -> new CostCenterResponse(e.getKey(), e.getValue())).toList();
    }

    private List<ExpenseAllocationService.Parte> partes(Long clienteId, List<FinancialTransaction> despesas) {
        return expenseAllocationService == null
                ? ExpenseAllocationService.legado(despesas)
                : expenseAllocationService.dividir(clienteId, despesas);
    }

}
