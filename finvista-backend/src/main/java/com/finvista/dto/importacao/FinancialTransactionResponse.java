package com.finvista.dto.importacao;

import com.finvista.model.FinancialTransaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinancialTransactionResponse(
        Long id,
        LocalDate data,
        String descricao,
        String tipo,
        BigDecimal valor,
        String categoria,
        String centroCusto,
        String origem,
        String documentoReferencia
        ) {

    public static FinancialTransactionResponse from(
            FinancialTransaction transaction
    ) {
        if (transaction == null) {
            throw new IllegalArgumentException(
                    "O lançamento financeiro não pode ser nulo."
            );
        }

        return new FinancialTransactionResponse(
                transaction.getId(),
                transaction.getData(),
                transaction.getDescricao(),
                transaction.getTipo(),
                transaction.getValor(),
                transaction.getCategoria(),
                transaction.getCentroCusto(),
                transaction.getOrigem(),
                transaction.getDocumentoReferencia()
        );
    }
}

