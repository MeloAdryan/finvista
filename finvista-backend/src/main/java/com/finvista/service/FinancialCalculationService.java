package com.finvista.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class FinancialCalculationService {

    private static final BigDecimal CEM =
            new BigDecimal("100");

    private static final int ESCALA_PERCENTUAL = 2;


    public BigDecimal calcularResultado(
            BigDecimal receita,
            BigDecimal despesa
    ) {
        validarValorNaoNegativo(receita, "Receita");
        validarValorNaoNegativo(despesa, "Despesa");

        return receita.subtract(despesa);
    }


    public BigDecimal calcularMargem(
            BigDecimal receita,
            BigDecimal resultado
    ) {
        validarValorNaoNegativo(receita, "Receita");

        if (resultado == null) {
            throw new IllegalArgumentException(
                    "Resultado não pode ser nulo."
            );
        }

        if (receita.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO
                    .setScale(
                            ESCALA_PERCENTUAL,
                            RoundingMode.HALF_UP
                    );
        }

        return resultado
                .multiply(CEM)
                .divide(
                        receita,
                        ESCALA_PERCENTUAL,
                        RoundingMode.HALF_UP
                );
    }


    public BigDecimal calcularSaldoFuturo(
            BigDecimal saldoAnterior,
            BigDecimal receita,
            BigDecimal despesa
    ) {
        if (saldoAnterior == null) {
            throw new IllegalArgumentException(
                    "Saldo anterior não pode ser nulo."
            );
        }

        return saldoAnterior.add(
                calcularResultado(receita, despesa)
        );
    }


    public BigDecimal calcularValorPonderado(
            BigDecimal valor,
            Integer probabilidade
    ) {
        validarValorNaoNegativo(valor, "Valor");

        if (probabilidade == null) {
            throw new IllegalArgumentException(
                    "Probabilidade não pode ser nula."
            );
        }

        if (probabilidade < 0 || probabilidade > 100) {
            throw new IllegalArgumentException(
                    "Probabilidade deve estar entre 0 e 100."
            );
        }

        return valor
                .multiply(
                        BigDecimal.valueOf(probabilidade)
                )
                .divide(
                        CEM,
                        2,
                        RoundingMode.HALF_UP
                );
    }


    public BigDecimal calcularPercentualUtilizado(
            BigDecimal gasto,
            BigDecimal meta
    ) {
        validarValorNaoNegativo(gasto, "Gasto");

        if (meta == null
                || meta.compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Meta de gastos deve ser maior que zero."
            );
        }

        return gasto
                .multiply(CEM)
                .divide(
                        meta,
                        ESCALA_PERCENTUAL,
                        RoundingMode.HALF_UP
                );
    }


    public BigDecimal calcularSaldoMeta(
            BigDecimal meta,
            BigDecimal gasto
    ) {
        if (meta == null
                || meta.compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Meta de gastos deve ser maior que zero."
            );
        }

        validarValorNaoNegativo(gasto, "Gasto");

        return meta.subtract(gasto);
    }


    private void validarValorNaoNegativo(
            BigDecimal valor,
            String campo
    ) {
        if (valor == null) {
            throw new IllegalArgumentException(
                    campo + " não pode ser nulo."
            );
        }

        if (valor.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    campo + " não pode ser negativo."
            );
        }
    }
}