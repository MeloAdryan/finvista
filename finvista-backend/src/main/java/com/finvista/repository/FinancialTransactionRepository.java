package com.finvista.repository;

import com.finvista.model.FinancialTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface FinancialTransactionRepository
        extends JpaRepository<FinancialTransaction, Long> {

    /*
     * ============================================================
     * CONSULTAS LEGADAS
     * ============================================================
     *
     * Mantidas temporariamente para não quebrar os serviços
     * existentes enquanto o FinVista é migrado para o isolamento
     * completo por Cliente.
     */
    List<FinancialTransaction> findAllByOrderByDataDesc();

    List<FinancialTransaction> findByDataBetweenOrderByDataAsc(
            LocalDate dataInicial,
            LocalDate dataFinal
    );

    List<FinancialTransaction> findByTipoOrderByDataDesc(
            String tipo
    );

    List<FinancialTransaction> findByTipoAndDataBetweenOrderByDataAsc(
            String tipo,
            LocalDate dataInicial,
            LocalDate dataFinal
    );

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

    boolean existsByDataAndDescricaoAndTipoAndValorAndDocumentoReferencia(
            LocalDate data,
            String descricao,
            String tipo,
            BigDecimal valor,
            String documentoReferencia
    );

    List<FinancialTransaction> findByOrigemOrderByIdAsc(
            String origem
    );

    @Query("""
        SELECT MIN(l.data)
        FROM FinancialTransaction l
        WHERE l.data IS NOT NULL
        """)
    Optional<LocalDate> findMenorData();

    @Query("""
        SELECT MAX(l.data)
        FROM FinancialTransaction l
        WHERE l.data IS NOT NULL
        """)
    Optional<LocalDate> findMaiorData();
}
            
    
            
