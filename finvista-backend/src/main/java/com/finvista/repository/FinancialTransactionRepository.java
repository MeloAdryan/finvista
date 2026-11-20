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

        @Query("select t from FinancialTransaction t where t.cliente.id=:cliente and t.origem='CONTA_AZUL_API' and t.dataVencimento between :inicio and :fim")
        List<FinancialTransaction> paraConferencia(@org.springframework.data.repository.query.Param("cliente") Long cliente,
                @org.springframework.data.repository.query.Param("inicio") LocalDate inicio, @org.springframework.data.repository.query.Param("fim") LocalDate fim);

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

        @Query("select t from FinancialTransaction t where t.cliente.id=:cliente and t.origem=:origem and lower(t.documentoReferencia)=:referencia order by t.id")
        List<FinancialTransaction> identidadeApi(@org.springframework.data.repository.query.Param("cliente") Long cliente,
                @org.springframework.data.repository.query.Param("origem") String origem,
                @org.springframework.data.repository.query.Param("referencia") String referencia,
                org.springframework.data.domain.Pageable pagina);

        @Query("select count(t)>0 from FinancialTransaction t where t.cliente.id=:cliente and t.origem=:origem and (t.documentoReferencia is null or trim(t.documentoReferencia)='')")
        boolean apiSemIdentidade(@org.springframework.data.repository.query.Param("cliente") Long cliente,
                @org.springframework.data.repository.query.Param("origem") String origem);

        @Query("""
        select t from FinancialTransaction t where t.cliente.id=:cliente
          and (:somenteExternos=false or t.origem is null or t.origem<>'CONTA_AZUL_API') and (
          lower(t.documentoReferencia)=:referencia or
          (t.tipo=:tipo and (t.dataVencimento=:vencimento or coalesce(t.dataCompetencia,t.data)=:competencia)
            and (lower(trim(t.descricao))=lower(:descricao) or coalesce(t.valorOriginal,t.valor)=:principal)))
        order by t.id
        """)
        List<FinancialTransaction> candidatosApi(@org.springframework.data.repository.query.Param("cliente") Long cliente,
                @org.springframework.data.repository.query.Param("referencia") String referencia,
                @org.springframework.data.repository.query.Param("tipo") String tipo,
                @org.springframework.data.repository.query.Param("vencimento") LocalDate vencimento,
                @org.springframework.data.repository.query.Param("competencia") LocalDate competencia,
                @org.springframework.data.repository.query.Param("descricao") String descricao,
                @org.springframework.data.repository.query.Param("principal") BigDecimal principal,
                @org.springframework.data.repository.query.Param("somenteExternos") boolean somenteExternos,
                org.springframework.data.domain.Pageable pagina);

    }
