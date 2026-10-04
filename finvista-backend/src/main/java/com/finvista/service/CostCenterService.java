package com.finvista.service;

import com.finvista.dto.CostCenterResponse;
import com.finvista.model.FinancialTransaction;
import com.finvista.repository.FinancialTransactionRepository;
import java.time.YearMonth;
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
    
    public List<CostCenterResponse> listarNoMes(YearMonth mes) {
        if (mes == null) {
            throw new IllegalArgumentException("Mês de análise é obrigatório.");
        }

        Long clienteId = obterClienteIdAtual();

        List<FinancialTransaction> lancamentos = financialTransactionRepository
                .findByClienteIdAndDataBetweenOrderByDataAsc(
                        clienteId, mes.atDay(1), mes.atEndOfMonth()
                );

        Map<String, BigDecimal> totais = lancamentos.stream()
                .filter(lancamento -> lancamento.getData() != null
                        && mes.equals(YearMonth.from(lancamento.getData()))
                        && lancamento.getValor() != null
                        && lancamento.getTipo() != null
                        && TIPO_DESPESA.equalsIgnoreCase(
                                lancamento.getTipo().trim()
                        ))
                .collect(Collectors.groupingBy(
                        lancamento -> {
                                    String centro = lancamento.getCentroCusto();
                                    return centro == null || centro.isBlank()
                                            ? "Sem centro de custo"
                                            : centro.trim();
                                },
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                FinancialTransaction::getValor,
                                BigDecimal::add
                        )
                ));

        return totais.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(entrada -> new CostCenterResponse(
                        entrada.getKey(), entrada.getValue()
                ))
                .toList();
    }

}