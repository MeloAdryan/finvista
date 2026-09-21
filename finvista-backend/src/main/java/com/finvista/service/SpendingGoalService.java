package com.finvista.service;

import com.finvista.dto.SpendingGoalResponse;
import com.finvista.model.FinancialTransaction;
import com.finvista.model.SpendingGoal;
import com.finvista.repository.FinancialTransactionRepository;
import com.finvista.repository.SpendingGoalRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;



@Service
public class SpendingGoalService {

    private static final String TIPO_MENSAL =
            "MENSAL";

    private static final String TIPO_SEMESTRAL =
            "SEMESTRAL";

    private static final String TIPO_DESPESA =
            "DESPESA";

    private final SpendingGoalRepository spendingGoalRepository;

    private final FinancialTransactionRepository
            financialTransactionRepository;

    private final FinancialCalculationService
            calculationService;

    public SpendingGoalService(
            SpendingGoalRepository spendingGoalRepository,
            FinancialTransactionRepository financialTransactionRepository,
            FinancialCalculationService calculationService
    ) {
        this.spendingGoalRepository =
                spendingGoalRepository;

        this.financialTransactionRepository =
                financialTransactionRepository;

        this.calculationService =
                calculationService;
    }

    public SpendingGoal salvar(
            SpendingGoal meta
    ) {
        validarMeta(meta);

        meta.setTipo(
                meta.getTipo()
                        .trim()
                        .toUpperCase()
        );

        return spendingGoalRepository.save(meta);
    }

    public List<SpendingGoalResponse> listarComSituacao(){

        return spendingGoalRepository
                .findAllByOrderByDataInicioDesc()
                .stream()
                .map(this::calcularSituacao)
                .toList();
    }

   public SpendingGoalResponse buscarSituacao(
        Long id
) {
        SpendingGoal meta =
                spendingGoalRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Meta de gastos não encontrada: "
                                                + id
                                )
                        );

        return calcularSituacao(meta);
    }

    private SpendingGoalResponse calcularSituacao(
        SpendingGoal meta
){
        List<FinancialTransaction> despesas =
                financialTransactionRepository
                        .findByTipoAndDataBetweenOrderByDataAsc(
                                TIPO_DESPESA,
                                meta.getDataInicio(),
                                meta.getDataFim()
                        );

        BigDecimal gastoAtual =
                despesas.stream()
                        .map(FinancialTransaction::getValor)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        BigDecimal percentualUtilizado =
                calculationService
                        .calcularPercentualUtilizado(
                                gastoAtual,
                                meta.getValorLimite()
                        );

        BigDecimal saldoMeta =
                calculationService
                        .calcularSaldoMeta(
                                meta.getValorLimite(),
                                gastoAtual
                        );

        String status =
                calcularStatus(
                        percentualUtilizado,
                        meta.getPercentualAlerta()
                );

        return new SpendingGoalResponse(
        meta.getId(),
        meta.getTipo(),
        meta.getDataInicio(),
        meta.getDataFim(),
        meta.getValorLimite(),
        gastoAtual,
        percentualUtilizado,
        saldoMeta,
        meta.getPercentualAlerta(),
        status
);
}

    private String calcularStatus(
            BigDecimal percentualUtilizado,
            Integer percentualAlerta
    ) {
        if (percentualUtilizado.compareTo(
                new BigDecimal("100")
        ) > 0) {
            return "EXCEDIDA";
        }

        if (percentualUtilizado.compareTo(
                BigDecimal.valueOf(percentualAlerta)
        ) >= 0) {
            return "ALERTA";
        }

        return "NORMAL";
    }

    private void validarMeta(
            SpendingGoal meta
    ) {
        if (meta == null) {
            throw new IllegalArgumentException(
                    "Meta de gastos não pode ser nula."
            );
        }

        validarTipo(meta.getTipo());

        validarPeriodo(
                meta.getTipo(),
                meta.getDataInicio(),
                meta.getDataFim()
        );

        if (meta.getValorLimite() == null
                || meta.getValorLimite()
                .compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Valor limite deve ser maior que zero."
            );
        }

        if (meta.getPercentualAlerta() == null
                || meta.getPercentualAlerta() <= 0
                || meta.getPercentualAlerta() > 100) {

            throw new IllegalArgumentException(
                    "Percentual de alerta deve estar entre 1 e 100."
            );
        }
    }

    private void validarTipo(
            String tipo
    ) {
        if (tipo == null || tipo.isBlank()) {
            throw new IllegalArgumentException(
                    "Tipo da meta é obrigatório."
            );
        }

        String tipoNormalizado =
                tipo.trim().toUpperCase();

        if (!TIPO_MENSAL.equals(tipoNormalizado)
                && !TIPO_SEMESTRAL.equals(tipoNormalizado)) {

            throw new IllegalArgumentException(
                    "Tipo da meta deve ser MENSAL ou SEMESTRAL."
            );
        }
    }

    private void validarPeriodo(
            String tipo,
            LocalDate dataInicio,
            LocalDate dataFim
    ) {
        if (dataInicio == null || dataFim == null) {
            throw new IllegalArgumentException(
                    "Data inicial e data final são obrigatórias."
            );
        }

        if (dataFim.isBefore(dataInicio)) {
            throw new IllegalArgumentException(
                    "Data final não pode ser anterior à data inicial."
            );
        }

        String tipoNormalizado =
                tipo.trim().toUpperCase();

        if (TIPO_MENSAL.equals(tipoNormalizado)) {

            LocalDate inicioEsperado =
                    dataInicio.withDayOfMonth(1);

            LocalDate fimEsperado =
                    dataInicio.withDayOfMonth(
                            dataInicio.lengthOfMonth()
                    );

            if (!dataInicio.equals(inicioEsperado)
                    || !dataFim.equals(fimEsperado)) {

                throw new IllegalArgumentException(
                        "Meta MENSAL deve compreender um mês completo."
                );
            }
        }

        if (TIPO_SEMESTRAL.equals(tipoNormalizado)) {

            LocalDate inicioEsperado;

            if (dataInicio.getMonthValue() <= 6) {
                inicioEsperado =
                        LocalDate.of(
                                dataInicio.getYear(),
                                1,
                                1
                        );
            } else {
                inicioEsperado =
                        LocalDate.of(
                                dataInicio.getYear(),
                                7,
                                1
                        );
            }

            LocalDate fimEsperado =
                    inicioEsperado.plusMonths(6)
                            .minusDays(1);

            if (!dataInicio.equals(inicioEsperado)
                    || !dataFim.equals(fimEsperado)) {

                throw new IllegalArgumentException(
                        "Meta SEMESTRAL deve compreender um semestre completo."
                );
            }
        }
    }
}