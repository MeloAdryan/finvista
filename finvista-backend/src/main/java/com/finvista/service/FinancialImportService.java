package com.finvista.service;

import com.finvista.model.Cliente;
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
import java.util.HashSet;
import java.util.Set;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class FinancialImportService {

    private static final String ORIGEM_CONTA_AZUL
            = "CONTA_AZUL_EXCEL";

    private final FinancialTransactionRepository repository;
    private final ClienteContextService clienteContextService;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    public FinancialImportService(
            FinancialTransactionRepository repository,
            ClienteContextService clienteContextService
    ) {
        this.repository = repository;
        this.clienteContextService = clienteContextService;
    }

    // Construtor explícito para testar o bloqueio sem banco real.
    public FinancialImportService(FinancialTransactionRepository repository,
            ClienteContextService context, EntityManager entityManager) {
        this(repository, context);
        this.entityManager = entityManager;
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

        Cliente cliente
                = clienteContextService.getClienteAtual();

        // Mesmo bloqueio usado pela aplicação do plano Conta Azul.
        // A segunda importação aguarda o commit da primeira antes de conferir duplicidade.
        Cliente bloqueado = entityManager.find(Cliente.class, cliente.getId(), LockModeType.PESSIMISTIC_WRITE);
        if (bloqueado == null || !Boolean.TRUE.equals(bloqueado.getAtivo())) {
            throw new IllegalStateException("A empresa não existe ou está inativa; nenhum lançamento foi importado.");
        }
        prepararLancamentosParaCliente(lancamentos, bloqueado);

        if (ehImportacaoContaAzul(lancamentos)) {
            return salvarContaAzul(
                    lancamentos,
                    cliente.getId()
            );
        }

        /*
     * Outros formatos continuam usando a estratégia legada
     * enquanto a nova arquitetura de importação é evoluída.
         */
        return salvarFormatoLegado(
                lancamentos,
                cliente.getId()
        );
    }

    private List<FinancialTransaction> salvarContaAzul(
            List<FinancialTransaction> lancamentos,
            Long clienteId
    ) {

        List<FinancialTransaction> existentes
                = repository.findByClienteIdAndOrigemOrderByIdAsc(
                        clienteId,
                        ORIGEM_CONTA_AZUL
                );

        Map<ChaveContaAzul, Integer> quantidadeExistente
                = new HashMap<>();

        for (FinancialTransaction existente : existentes) {

            ChaveContaAzul chave
                    = criarChaveContaAzul(existente);

            quantidadeExistente.merge(
                    chave,
                    1,
                    Integer::sum
            );
        }

        Map<ChaveContaAzul, Integer> quantidadeNoArquivo
                = new HashMap<>();

        List<FinancialTransaction> novosLancamentos
                = new ArrayList<>();

        for (FinancialTransaction lancamento : lancamentos) {

            ChaveContaAzul chave
                    = criarChaveContaAzul(lancamento);

            int ocorrenciaAtual
                    = quantidadeNoArquivo.merge(
                            chave,
                            1,
                            Integer::sum
                    );

            int ocorrenciasJaExistentes
                    = quantidadeExistente.getOrDefault(
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

    private record ChaveLegada(LocalDate data, String descricao, String tipo,
            BigDecimal valor, String documento, String categoria, String centro, String origem) {}

    private ChaveLegada chaveLegada(FinancialTransaction t) {
        return new ChaveLegada(t.getData(), normalizarTexto(t.getDescricao()),
                normalizarTexto(t.getTipo()), normalizarValor(t.getValor()),
                normalizarTexto(t.getDocumentoReferencia()), normalizarTexto(t.getCategoria()),
                normalizarTexto(t.getCentroCusto()), normalizarTexto(t.getOrigem()));
    }

    private List<FinancialTransaction> salvarFormatoLegado(
            List<FinancialTransaction> lancamentos, Long clienteId) {
        Set<ChaveLegada> arquivo = new HashSet<>();
        for (FinancialTransaction lancamento : lancamentos) {
            if (!arquivo.add(chaveLegada(lancamento))) {
                throw new IllegalArgumentException("O arquivo contém lançamentos completamente idênticos. "
                        + "Confira as linhas repetidas. Se forem operações diferentes, informe documentos "
                        + "de referência distintos. Nenhum lançamento deste arquivo foi gravado.");
            }
        }
        Set<ChaveLegada> existentes = new HashSet<>();
        for (FinancialTransaction existente : repository.findAllByClienteIdOrderByDataDesc(clienteId)) {
            existentes.add(chaveLegada(existente));
        }
        List<FinancialTransaction> novos = new ArrayList<>();
        for (FinancialTransaction lancamento : lancamentos) {
            if (!existentes.contains(chaveLegada(lancamento))) {
                novos.add(lancamento);
            }
        }
        return novos.isEmpty() ? List.of() : repository.saveAll(novos);
    }

    private void prepararLancamentosParaCliente(
            List<FinancialTransaction> lancamentos,
            Cliente cliente
    ) {

        if (cliente == null || cliente.getId() == null) {
            throw new IllegalStateException(
                    "Nenhum cliente válido foi encontrado para a importação."
            );
        }

        for (FinancialTransaction lancamento : lancamentos) {

            if (lancamento == null) {
                throw new IllegalArgumentException(
                        "A importação contém um lançamento financeiro inválido."
                );
            }

            lancamento.setCliente(cliente);
        }
    }

    private boolean ehImportacaoContaAzul(
            List<FinancialTransaction> lancamentos
    ) {

        return lancamentos.stream()
                .allMatch(lancamento
                        -> ORIGEM_CONTA_AZUL.equalsIgnoreCase(
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
