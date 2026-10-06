package com.finvista.service;

import com.finvista.dto.SpendingGoalResponse;
import com.finvista.model.FinancialTransaction;
import com.finvista.model.SpendingGoal;
import com.finvista.repository.FinancialTransactionRepository;
import com.finvista.repository.SpendingGoalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
public class SpendingGoalService {

    private static final String TIPO_MENSAL = "MENSAL";
    private static final String TIPO_SEMESTRAL = "SEMESTRAL";
    private static final String TIPO_DESPESA = "DESPESA";

    private final SpendingGoalRepository spendingGoalRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final FinancialCalculationService calculationService;
    private final ClienteContextService clienteContextService;

    private ExpenseAllocationService expenseAllocationService;

    // O Spring usa este construtor para incluir os rateios nos cálculos.
    @Autowired
    public SpendingGoalService(
            SpendingGoalRepository goals,
            FinancialTransactionRepository transactions,
            FinancialCalculationService calculation,
            ClienteContextService context,
            ExpenseAllocationService allocations
    ) {
        this(goals, transactions, calculation, context);
        this.expenseAllocationService = Objects.requireNonNull(allocations);
    }

    // Mantém a compatibilidade com os testes anteriores.
    public SpendingGoalService(
            SpendingGoalRepository spendingGoalRepository,
            FinancialTransactionRepository financialTransactionRepository,
            FinancialCalculationService calculationService,
            ClienteContextService clienteContextService
    ) {
        this.spendingGoalRepository = spendingGoalRepository;
        this.financialTransactionRepository = financialTransactionRepository;
        this.calculationService = calculationService;
        this.clienteContextService = clienteContextService;
    }

    public SpendingGoal salvar(SpendingGoal meta) {
        validarMeta(meta);

        meta.setTipo(meta.getTipo().trim().toUpperCase());

        // O proprietário vem do contexto do usuário conectado.
        meta.setCliente(clienteContextService.getClienteAtual());

        return spendingGoalRepository.save(meta);
    }

    public List<SpendingGoal> listarMetas() {
        Long clienteId = obterClienteIdAtual();

        return spendingGoalRepository
                .findByClienteIdOrderByDataInicioDesc(clienteId);
    }

    public SpendingGoal buscarPorId(Long id) {
        Long clienteId = obterClienteIdAtual();

        return spendingGoalRepository
                .findByIdAndClienteId(id, clienteId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Meta de gastos não encontrada: " + id
                ));
    }

    public List<SpendingGoalResponse> listarComSituacao() {
        return listarComSituacao(null, null);
    }

    public List<SpendingGoalResponse> listarComSituacao(
            String centroCusto,
            String categoria
    ) {
        String centroCustoNormalizado = normalizarFiltro(centroCusto);
        String categoriaNormalizada = normalizarFiltro(categoria);

        // Os filtros restringem as despesas exibidas, sem remover metas.
        return listarMetas()
                .stream()
                .map(meta -> calcularSituacao(
                        meta,
                        centroCustoNormalizado,
                        categoriaNormalizada
                ))
                .toList();
    }

    public SpendingGoalResponse buscarSituacao(Long id) {
        SpendingGoal meta = buscarPorId(id);

        return calcularSituacao(meta, null, null);
    }

    public SpendingGoalResponse buscarSituacao(
            Long id,
            String centroCusto,
            String categoria
    ) {
        SpendingGoal meta = buscarPorId(id);

        return calcularSituacao(
                meta,
                normalizarFiltro(centroCusto),
                normalizarFiltro(categoria)
        );
    }

    private SpendingGoalResponse calcularSituacao(
            SpendingGoal meta,
            String filtroCentroCusto,
            String filtroCategoria
    ) {
        String centroCusto = normalizarFiltro(meta.getCentroCusto());
        String categoria = normalizarFiltro(meta.getCategoria());

        Long clienteId = meta.getCliente().getId();

        // Busca apenas despesas do proprietário e do período da meta.
        List<FinancialTransaction> despesas = financialTransactionRepository
                .findByClienteIdAndTipoAndDataBetweenOrderByDataAsc(
                        clienteId,
                        TIPO_DESPESA,
                        meta.getDataInicio(),
                        meta.getDataFim()
                );

        // Divide as despesas e aplica os critérios cadastrados na meta.
        List<ExpenseAllocationService.Parte> despesasDaMeta =
                partes(clienteId, despesas)
                        .stream()
                        .filter(parte -> correspondeCentroCusto(
                                parte,
                                centroCusto
                        ))
                        .filter(parte -> correspondeCategoria(
                                parte,
                                categoria
                        ))
                        .toList();

        BigDecimal gastoAtual = somarDespesas(despesasDaMeta);

        // Combina os critérios da meta com os filtros escolhidos na tela.
        List<ExpenseAllocationService.Parte> despesasNoFiltro =
                despesasDaMeta.stream()
                        .filter(parte -> correspondeCentroCusto(
                                parte,
                                filtroCentroCusto
                        ))
                        .filter(parte -> correspondeCategoria(
                                parte,
                                filtroCategoria
                        ))
                        .toList();

        BigDecimal gastoFiltrado = somarDespesas(despesasNoFiltro);

        BigDecimal percentualFiltrado =
                calculationService.calcularPercentualUtilizado(
                        gastoFiltrado,
                        meta.getValorLimite()
                );

        BigDecimal percentualUtilizado =
                calculationService.calcularPercentualUtilizado(
                        gastoAtual,
                        meta.getValorLimite()
                );

        BigDecimal saldoMeta = calculationService.calcularSaldoMeta(
                meta.getValorLimite(),
                gastoAtual
        );

        // O status usa o consumo da meta, independentemente do filtro da tela.
        String status = calcularStatus(
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
                status,
                categoria,
                centroCusto,
                gastoFiltrado,
                percentualFiltrado,
                filtroCategoria,
                filtroCentroCusto
        );
    }

    private boolean correspondeCentroCusto(
            ExpenseAllocationService.Parte parte,
            String centroCusto
    ) {
        return centroCusto == null
                || parte.centroCusto().equalsIgnoreCase(centroCusto);
    }

    private boolean correspondeCategoria(
            ExpenseAllocationService.Parte parte,
            String categoria
    ) {
        return categoria == null
                || parte.categoria().equalsIgnoreCase(categoria);
    }

    private BigDecimal somarDespesas(
            List<ExpenseAllocationService.Parte> despesas
    ) {
        return despesas.stream()
                .map(ExpenseAllocationService.Parte::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<ExpenseAllocationService.Parte> partes(
            Long clienteId,
            List<FinancialTransaction> despesas
    ) {
        return expenseAllocationService == null
                ? ExpenseAllocationService.legado(despesas)
                : expenseAllocationService.dividir(clienteId, despesas);
    }

    private String normalizarFiltro(String valor) {
        if (valor == null) {
            return null;
        }

        String valorNormalizado = valor.trim();

        return valorNormalizado.isEmpty()
                ? null
                : valorNormalizado;
    }

    private Long obterClienteIdAtual() {
        return clienteContextService.getClienteAtualId();
    }

    private String calcularStatus(
            BigDecimal percentualUtilizado,
            Integer percentualAlerta
    ) {
        if (percentualUtilizado.compareTo(new BigDecimal("100")) > 0) {
            return "EXCEDIDA";
        }

        if (percentualUtilizado.compareTo(
                BigDecimal.valueOf(percentualAlerta)
        ) >= 0) {
            return "ALERTA";
        }

        return "NORMAL";
    }

    private void validarMeta(SpendingGoal meta) {
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
                || meta.getValorLimite().compareTo(BigDecimal.ZERO) <= 0) {
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

    private void validarTipo(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            throw new IllegalArgumentException(
                    "Tipo da meta é obrigatório."
            );
        }

        String tipoNormalizado = tipo.trim().toUpperCase();

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

        String tipoNormalizado = tipo.trim().toUpperCase();

        if (TIPO_MENSAL.equals(tipoNormalizado)) {
            LocalDate inicioEsperado = dataInicio.withDayOfMonth(1);

            LocalDate fimEsperado = dataInicio.withDayOfMonth(
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
            int mesInicial = dataInicio.getMonthValue() <= 6 ? 1 : 7;

            LocalDate inicioEsperado = LocalDate.of(
                    dataInicio.getYear(),
                    mesInicial,
                    1
            );

            LocalDate fimEsperado = inicioEsperado
                    .plusMonths(6)
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