package com.finvista.service;

import com.finvista.dto.CostCenterResponse;
import com.finvista.model.FinancialTransaction;
import com.finvista.repository.FinancialTransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CostCenterService {

    private static final String TIPO_DESPESA = "DESPESA";

    private final FinancialTransactionRepository financialTransactionRepository;
    private final ClienteContextService clienteContextService;

    public CostCenterService(
            FinancialTransactionRepository financialTransactionRepository,
            ClienteContextService clienteContextService
    ) {
        this.financialTransactionRepository =
                financialTransactionRepository;

        this.clienteContextService =
                clienteContextService;
    }

    public List<CostCenterResponse> listar() {

        Long clienteId = obterClienteIdAtual();

        List<FinancialTransaction> despesas =
                financialTransactionRepository
                        .findByClienteIdAndTipoOrderByDataDesc(
                                clienteId,
                                TIPO_DESPESA
                        );

        Map<String, BigDecimal> totaisPorCentro =
                despesas.stream()
                        .filter(
                                lancamento ->
                                        lancamento.getCentroCusto() != null
                                                && !lancamento
                                                        .getCentroCusto()
                                                        .isBlank()
                        )
                        .collect(
                                Collectors.groupingBy(
                                        lancamento ->
                                                lancamento
                                                        .getCentroCusto()
                                                        .trim(),
                                        Collectors.reducing(
                                                BigDecimal.ZERO,
                                                this::obterValor,
                                                BigDecimal::add
                                        )
                                )
                        );

        return totaisPorCentro
                .entrySet()
                .stream()
                .sorted(
                        Map.Entry
                                .<String, BigDecimal>comparingByValue()
                                .reversed()
                )
                .map(
                        entrada ->
                                new CostCenterResponse(
                                        entrada.getKey(),
                                        entrada.getValue()
                                )
                )
                .toList();
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
}