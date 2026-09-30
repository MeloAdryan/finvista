package com.finvista.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.finvista.model.FinancialTransaction;

public interface FinancialTransactionRepository
        extends JpaRepository<FinancialTransaction, Long> {


    List<FinancialTransaction> findAllByOrderByDataDesc();

    /*
     * ============================================================
     * CONSULTAS ISOLADAS POR CLIENTE
     * ============================================================
     */

    List<FinancialTransaction> findAllByClienteIdOrderByDataDesc(
            Long clienteId
    );

    List<FinancialTransaction> findByClienteIdAndDataBetweenOrderByDataAsc(
            Long clienteId,
            LocalDate dataInicial,
            LocalDate dataFinal
    );

    List<FinancialTransaction> findByClienteIdAndTipoOrderByDataDesc(
            Long clienteId,
            String tipo
    );

    List<FinancialTransaction> findByClienteIdAndTipoAndDataBetweenOrderByDataAsc(
            Long clienteId,
            String tipo,
            LocalDate dataInicial,
            LocalDate dataFinal
    );

    List<FinancialTransaction> findByClienteId(
            Long clienteId
    );

    /*
     * ============================================================
     * IMPORTAÇÃO ISOLADA POR CLIENTE
     * ============================================================
     */

    List<FinancialTransaction> findByClienteIdAndOrigemOrderByIdAsc(
            Long clienteId,
            String origem
    );

    boolean existsByClienteIdAndDataAndDescricaoAndTipoAndValorAndDocumentoReferencia(
            Long clienteId,
            LocalDate data,
            String descricao,
            String tipo,
            BigDecimal valor,
            String documentoReferencia
    );

    /*
     * ============================================================
     * INTERVALO POR CLIENTE
     * ============================================================
     */

    @Query("""
            SELECT MIN(l.data)
            FROM FinancialTransaction l
            WHERE l.cliente.id = :clienteId
              AND l.data IS NOT NULL
            """)
    Optional<LocalDate> findMenorDataByClienteId(
            Long clienteId
    );

    @Query("""
            SELECT MAX(l.data)
            FROM FinancialTransaction l
            WHERE l.cliente.id = :clienteId
              AND l.data IS NOT NULL
            """)
    Optional<LocalDate> findMaiorDataByClienteId(
            Long clienteId
    );
}