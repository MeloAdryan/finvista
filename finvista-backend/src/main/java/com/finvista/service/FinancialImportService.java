package com.finvista.service;

import com.finvista.model.FinancialTransaction;
import com.finvista.repository.FinancialTransactionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class FinancialImportService {

    private final FinancialTransactionRepository repository;

    public FinancialImportService(
            FinancialTransactionRepository repository
    ) {
        this.repository = repository;
    }

    @Transactional
    public List<FinancialTransaction> salvar(
            List<FinancialTransaction> lancamentos
    ) {

        if (lancamentos == null || lancamentos.isEmpty()) {
            throw new IllegalArgumentException(
                    "Nenhum lançamento financeiro foi informado para importação."
            );
        }

        List<FinancialTransaction> novosLancamentos =
                new ArrayList<>();

        Set<ChaveDuplicidade> chavesDoArquivo =
                new HashSet<>();

        for (FinancialTransaction lancamento : lancamentos) {

            ChaveDuplicidade chave =
                    criarChaveDuplicidade(lancamento);

            /*
             * Primeiro verificamos se o mesmo lançamento
             * já apareceu dentro do arquivo atual.
             */
            if (!chavesDoArquivo.add(chave)) {
                continue;
            }

            /*
             * Depois verificamos se o lançamento
             * já existe no banco de dados.
             */
            boolean duplicadoNoBanco =
                    repository
                            .existsByDataAndDescricaoAndTipoAndValorAndDocumentoReferencia(
                                    lancamento.getData(),
                                    lancamento.getDescricao(),
                                    lancamento.getTipo(),
                                    lancamento.getValor(),
                                    lancamento.getDocumentoReferencia()
                            );

            if (!duplicadoNoBanco) {
                novosLancamentos.add(lancamento);
            }
        }

        if (novosLancamentos.isEmpty()) {
            return List.of();
        }

        return repository.saveAll(novosLancamentos);
    }

    private ChaveDuplicidade criarChaveDuplicidade(
            FinancialTransaction lancamento
    ) {

        return new ChaveDuplicidade(
                lancamento.getData(),
                normalizarTexto(lancamento.getDescricao()),
                normalizarTexto(lancamento.getTipo()),
                normalizarValor(lancamento.getValor()),
                normalizarTexto(lancamento.getDocumentoReferencia())
        );
    }

    private String normalizarTexto(String valor) {

        if (valor == null) {
            return null;
        }

        return valor.trim();
    }

    private BigDecimal normalizarValor(BigDecimal valor) {

        if (valor == null) {
            return null;
        }

        return valor.stripTrailingZeros();
    }

    private static final class ChaveDuplicidade {

        private final LocalDate data;
        private final String descricao;
        private final String tipo;
        private final BigDecimal valor;
        private final String documentoReferencia;

        private ChaveDuplicidade(
                LocalDate data,
                String descricao,
                String tipo,
                BigDecimal valor,
                String documentoReferencia
        ) {
            this.data = data;
            this.descricao = descricao;
            this.tipo = tipo;
            this.valor = valor;
            this.documentoReferencia = documentoReferencia;
        }

        @Override
        public boolean equals(Object objeto) {

            if (this == objeto) {
                return true;
            }

            if (!(objeto instanceof ChaveDuplicidade outra)) {
                return false;
            }

            return Objects.equals(
                    data,
                    outra.data
            )
                    && Objects.equals(
                    descricao,
                    outra.descricao
            )
                    && Objects.equals(
                    tipo,
                    outra.tipo
            )
                    && Objects.equals(
                    valor,
                    outra.valor
            )
                    && Objects.equals(
                    documentoReferencia,
                    outra.documentoReferencia
            );
        }

        @Override
        public int hashCode() {

            return Objects.hash(
                    data,
                    descricao,
                    tipo,
                    valor,
                    documentoReferencia
            );
        }
    }
}