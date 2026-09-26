package com.finvista.service;

import com.finvista.model.FinancialTransaction;
import com.finvista.repository.FinancialTransactionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class FinancialImportService {

    private static final String ORIGEM_CONTA_AZUL =
            "CONTA_AZUL_EXCEL";

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

        /*
         * Para arquivos do Conta Azul usamos uma estratégia
         * de deduplicação mais completa.
         *
         * Ela preserva lançamentos legítimos semelhantes dentro
         * do mesmo arquivo e impede que a mesma ocorrência seja
         * gravada novamente em uma reimportação.
         */
        if (ehImportacaoContaAzul(lancamentos)) {
            return salvarContaAzul(lancamentos);
        }

        /*
         * Outros formatos continuam usando a estratégia legada
         * enquanto a nova arquitetura de importação é evoluída.
         */
        return salvarFormatoLegado(lancamentos);
    }

    private List<FinancialTransaction> salvarContaAzul(
            List<FinancialTransaction> lancamentos
    ) {

        List<FinancialTransaction> existentes =
                repository.findByOrigemOrderByIdAsc(
                        ORIGEM_CONTA_AZUL
                );

        /*
         * Em vez de guardar apenas "existe ou não existe",
         * contamos quantas ocorrências de cada identidade já
         * existem no banco.
         *
         * Isso é fundamental.
         *
         * Exemplo:
         *
         * Se o arquivo possui três lançamentos legitimamente
         * iguais segundo a identidade financeira, os três são
         * preservados na primeira importação.
         *
         * Ao importar o mesmo arquivo novamente, as três
         * ocorrências já existentes são reconhecidas.
         */
        Map<ChaveContaAzul, Integer> quantidadeExistente =
                new HashMap<>();

        for (FinancialTransaction existente : existentes) {

            ChaveContaAzul chave =
                    criarChaveContaAzul(existente);

            quantidadeExistente.merge(
                    chave,
                    1,
                    Integer::sum
            );
        }

        /*
         * Controla quantas ocorrências da mesma identidade
         * já apareceram no arquivo atual.
         */
        Map<ChaveContaAzul, Integer> quantidadeNoArquivo =
                new HashMap<>();

        List<FinancialTransaction> novosLancamentos =
                new ArrayList<>();

        for (FinancialTransaction lancamento : lancamentos) {

            ChaveContaAzul chave =
                    criarChaveContaAzul(lancamento);

            int ocorrenciaAtual =
                    quantidadeNoArquivo.merge(
                            chave,
                            1,
                            Integer::sum
                    );

            int ocorrenciasJaExistentes =
                    quantidadeExistente.getOrDefault(
                            chave,
                            0
                    );

            /*
             * A ocorrência N só é considerada duplicada
             * quando o banco já possui pelo menos N
             * ocorrências daquela mesma identidade.
             */
            if (ocorrenciaAtual <= ocorrenciasJaExistentes) {
                continue;
            }

            novosLancamentos.add(lancamento);
        }

        if (novosLancamentos.isEmpty()) {
            return List.of();
        }

        return repository.saveAll(novosLancamentos);
    }

    private List<FinancialTransaction> salvarFormatoLegado(
            List<FinancialTransaction> lancamentos
    ) {

        List<FinancialTransaction> novosLancamentos =
                new ArrayList<>();

        for (FinancialTransaction lancamento : lancamentos) {

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

    private boolean ehImportacaoContaAzul(
            List<FinancialTransaction> lancamentos
    ) {

        return lancamentos.stream()
                .allMatch(lancamento ->
                        ORIGEM_CONTA_AZUL.equalsIgnoreCase(
                                normalizarTexto(
                                        lancamento.getOrigem()
                                )
                        )
                );
    }

    private ChaveContaAzul criarChaveContaAzul(
            FinancialTransaction lancamento
    ) {

        return new ChaveContaAzul(
                lancamento.getDataCompetencia(),
                lancamento.getDataVencimento(),
                lancamento.getDataPrevista(),
                lancamento.getDataRealizacao(),

                normalizarTexto(lancamento.getDescricao()),
                normalizarTexto(lancamento.getTipo()),

                normalizarValor(lancamento.getValorOriginal()),
                normalizarValor(lancamento.getValorRealizado()),
                normalizarValor(lancamento.getValorAberto()),

                normalizarValor(lancamento.getJurosRealizado()),
                normalizarValor(lancamento.getMultaRealizada()),
                normalizarValor(lancamento.getDescontoRealizado()),

                normalizarValor(lancamento.getJurosPrevisto()),
                normalizarValor(lancamento.getMultaPrevista()),
                normalizarValor(lancamento.getDescontoPrevisto()),

                normalizarValor(lancamento.getValorTotalRealizado()),
                normalizarValor(lancamento.getValorTotalAberto()),

                normalizarTexto(lancamento.getEntidadeExternaId()),
                normalizarTexto(lancamento.getEntidadeNome()),
                normalizarTexto(lancamento.getCodigoReferencia()),
                normalizarTexto(lancamento.getSituacao()),
                normalizarTexto(lancamento.getRecorrencia()),
                normalizarTexto(lancamento.getQuantidadeRecorrencia()),

                lancamento.getAgendado(),

                normalizarTexto(lancamento.getFormaMovimentacao()),
                normalizarTexto(lancamento.getContaBancaria()),
                normalizarTexto(lancamento.getNotaFiscal()),
                normalizarTexto(lancamento.getObservacoes())
        );
    }

    private String normalizarTexto(String valor) {

        if (valor == null) {
            return null;
        }

        String normalizado = valor.trim();

        if (normalizado.isEmpty()) {
            return null;
        }

        return normalizado;
    }

    private BigDecimal normalizarValor(BigDecimal valor) {

        if (valor == null) {
            return null;
        }

        return valor.stripTrailingZeros();
    }

    private static final class ChaveContaAzul {

        private final LocalDate dataCompetencia;
        private final LocalDate dataVencimento;
        private final LocalDate dataPrevista;
        private final LocalDate dataRealizacao;

        private final String descricao;
        private final String tipo;

        private final BigDecimal valorOriginal;
        private final BigDecimal valorRealizado;
        private final BigDecimal valorAberto;

        private final BigDecimal jurosRealizado;
        private final BigDecimal multaRealizada;
        private final BigDecimal descontoRealizado;

        private final BigDecimal jurosPrevisto;
        private final BigDecimal multaPrevista;
        private final BigDecimal descontoPrevisto;

        private final BigDecimal valorTotalRealizado;
        private final BigDecimal valorTotalAberto;

        private final String entidadeExternaId;
        private final String entidadeNome;
        private final String codigoReferencia;
        private final String situacao;
        private final String recorrencia;
        private final String quantidadeRecorrencia;

        private final Boolean agendado;

        private final String formaMovimentacao;
        private final String contaBancaria;
        private final String notaFiscal;
        private final String observacoes;

        private ChaveContaAzul(
                LocalDate dataCompetencia,
                LocalDate dataVencimento,
                LocalDate dataPrevista,
                LocalDate dataRealizacao,
                String descricao,
                String tipo,
                BigDecimal valorOriginal,
                BigDecimal valorRealizado,
                BigDecimal valorAberto,
                BigDecimal jurosRealizado,
                BigDecimal multaRealizada,
                BigDecimal descontoRealizado,
                BigDecimal jurosPrevisto,
                BigDecimal multaPrevista,
                BigDecimal descontoPrevisto,
                BigDecimal valorTotalRealizado,
                BigDecimal valorTotalAberto,
                String entidadeExternaId,
                String entidadeNome,
                String codigoReferencia,
                String situacao,
                String recorrencia,
                String quantidadeRecorrencia,
                Boolean agendado,
                String formaMovimentacao,
                String contaBancaria,
                String notaFiscal,
                String observacoes
        ) {
            this.dataCompetencia = dataCompetencia;
            this.dataVencimento = dataVencimento;
            this.dataPrevista = dataPrevista;
            this.dataRealizacao = dataRealizacao;
            this.descricao = descricao;
            this.tipo = tipo;
            this.valorOriginal = valorOriginal;
            this.valorRealizado = valorRealizado;
            this.valorAberto = valorAberto;
            this.jurosRealizado = jurosRealizado;
            this.multaRealizada = multaRealizada;
            this.descontoRealizado = descontoRealizado;
            this.jurosPrevisto = jurosPrevisto;
            this.multaPrevista = multaPrevista;
            this.descontoPrevisto = descontoPrevisto;
            this.valorTotalRealizado = valorTotalRealizado;
            this.valorTotalAberto = valorTotalAberto;
            this.entidadeExternaId = entidadeExternaId;
            this.entidadeNome = entidadeNome;
            this.codigoReferencia = codigoReferencia;
            this.situacao = situacao;
            this.recorrencia = recorrencia;
            this.quantidadeRecorrencia = quantidadeRecorrencia;
            this.agendado = agendado;
            this.formaMovimentacao = formaMovimentacao;
            this.contaBancaria = contaBancaria;
            this.notaFiscal = notaFiscal;
            this.observacoes = observacoes;
        }

        @Override
        public boolean equals(Object objeto) {

            if (this == objeto) {
                return true;
            }

            if (!(objeto instanceof ChaveContaAzul outra)) {
                return false;
            }

            return Objects.equals(dataCompetencia, outra.dataCompetencia)
                    && Objects.equals(dataVencimento, outra.dataVencimento)
                    && Objects.equals(dataPrevista, outra.dataPrevista)
                    && Objects.equals(dataRealizacao, outra.dataRealizacao)
                    && Objects.equals(descricao, outra.descricao)
                    && Objects.equals(tipo, outra.tipo)
                    && Objects.equals(valorOriginal, outra.valorOriginal)
                    && Objects.equals(valorRealizado, outra.valorRealizado)
                    && Objects.equals(valorAberto, outra.valorAberto)
                    && Objects.equals(jurosRealizado, outra.jurosRealizado)
                    && Objects.equals(multaRealizada, outra.multaRealizada)
                    && Objects.equals(descontoRealizado, outra.descontoRealizado)
                    && Objects.equals(jurosPrevisto, outra.jurosPrevisto)
                    && Objects.equals(multaPrevista, outra.multaPrevista)
                    && Objects.equals(descontoPrevisto, outra.descontoPrevisto)
                    && Objects.equals(valorTotalRealizado, outra.valorTotalRealizado)
                    && Objects.equals(valorTotalAberto, outra.valorTotalAberto)
                    && Objects.equals(entidadeExternaId, outra.entidadeExternaId)
                    && Objects.equals(entidadeNome, outra.entidadeNome)
                    && Objects.equals(codigoReferencia, outra.codigoReferencia)
                    && Objects.equals(situacao, outra.situacao)
                    && Objects.equals(recorrencia, outra.recorrencia)
                    && Objects.equals(quantidadeRecorrencia, outra.quantidadeRecorrencia)
                    && Objects.equals(agendado, outra.agendado)
                    && Objects.equals(formaMovimentacao, outra.formaMovimentacao)
                    && Objects.equals(contaBancaria, outra.contaBancaria)
                    && Objects.equals(notaFiscal, outra.notaFiscal)
                    && Objects.equals(observacoes, outra.observacoes);
        }

        @Override
        public int hashCode() {

            return Objects.hash(
                    dataCompetencia,
                    dataVencimento,
                    dataPrevista,
                    dataRealizacao,
                    descricao,
                    tipo,
                    valorOriginal,
                    valorRealizado,
                    valorAberto,
                    jurosRealizado,
                    multaRealizada,
                    descontoRealizado,
                    jurosPrevisto,
                    multaPrevista,
                    descontoPrevisto,
                    valorTotalRealizado,
                    valorTotalAberto,
                    entidadeExternaId,
                    entidadeNome,
                    codigoReferencia,
                    situacao,
                    recorrencia,
                    quantidadeRecorrencia,
                    agendado,
                    formaMovimentacao,
                    contaBancaria,
                    notaFiscal,
                    observacoes
            );
        }
    }
}