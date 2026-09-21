package com.finvista.repository;

import com.finvista.model.FinancialTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface FinancialTransactionRepository
        extends JpaRepository<FinancialTransaction, Long> {

    List<FinancialTransaction> findAllByOrderByDataDesc();

    List<FinancialTransaction> findByDataBetweenOrderByDataAsc(
            LocalDate dataInicial,
            LocalDate dataFinal
    );

    List<FinancialTransaction> findByTipoOrderByDataDesc(
            String tipo
    );

    List<FinancialTransaction>
    findByTipoAndDataBetweenOrderByDataAsc(
            String tipo,
            LocalDate dataInicial,
            LocalDate dataFinal
    );

    boolean existsByDataAndDescricaoAndTipoAndValorAndDocumentoReferencia(
            LocalDate data,
            String descricao,
            String tipo,
            BigDecimal valor,
            String documentoReferencia
    );
}