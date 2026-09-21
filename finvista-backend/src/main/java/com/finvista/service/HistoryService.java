package com.finvista.service;

import com.finvista.dto.HistoryResponse;
import com.finvista.model.FinancialHistory;
import com.finvista.repository.FinancialHistoryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class HistoryService {

    private static final DateTimeFormatter FORMATO_ENTRADA =
            DateTimeFormatter.ofPattern("yyyy-MM");

    private static final DateTimeFormatter FORMATO_SAIDA =
            DateTimeFormatter.ofPattern(
                    "MMM/yyyy",
                    new Locale("pt", "BR")
            );

    private final FinancialHistoryRepository financialHistoryRepository;
    private final FinancialCalculationService calculationService;

    public HistoryService(
            FinancialHistoryRepository financialHistoryRepository,
            FinancialCalculationService calculationService
    ) {
        this.financialHistoryRepository =
                financialHistoryRepository;

        this.calculationService =
                calculationService;
    }

    public List<HistoryResponse> listar(
            String inicio,
            String fim
    ) {
        YearMonth periodoInicial =
                converterPeriodo(inicio);

        YearMonth periodoFinal =
                converterPeriodo(fim);

        return financialHistoryRepository
                .findAllByOrderByPeriodoAsc()
                .stream()
                .filter(registro ->
                        estaNoPeriodo(
                                registro,
                                periodoInicial,
                                periodoFinal
                        )
                )
                .map(this::criarResposta)
                .toList();
    }

    private boolean estaNoPeriodo(
            FinancialHistory registro,
            YearMonth periodoInicial,
            YearMonth periodoFinal
    ) {
        YearMonth periodo =
                YearMonth.parse(
                        registro.getPeriodo(),
                        FORMATO_ENTRADA
                );

        boolean depoisDoInicio =
                periodoInicial == null ||
                !periodo.isBefore(periodoInicial);

        boolean antesDoFim =
                periodoFinal == null ||
                !periodo.isAfter(periodoFinal);

        return depoisDoInicio && antesDoFim;
    }

    private YearMonth converterPeriodo(
            String periodo
    ) {
        if (periodo == null || periodo.isBlank()) {
            return null;
        }

        return YearMonth.parse(
                periodo,
                FORMATO_ENTRADA
        );
    }

    private HistoryResponse criarResposta(
            FinancialHistory registro
    ) {
        YearMonth periodo =
                YearMonth.parse(
                        registro.getPeriodo(),
                        FORMATO_ENTRADA
                );

        BigDecimal resultado =
                calculationService.calcularResultado(
                        registro.getReceita(),
                        registro.getDespesa()
                );

        BigDecimal margem =
        calculationService.calcularMargem(
                registro.getReceita(),
                resultado
        );

        return new HistoryResponse(
                formatarPeriodo(periodo),
                registro.getReceita(),
                registro.getDespesa(),
                resultado,
                margem
        );
    }

    private String formatarPeriodo(
            YearMonth periodo
    ) {
        String periodoFormatado =
                periodo
                        .format(FORMATO_SAIDA)
                        .replace(".", "");

        return periodoFormatado
                .substring(0, 1)
                .toUpperCase()
                + periodoFormatado.substring(1);
    }
}