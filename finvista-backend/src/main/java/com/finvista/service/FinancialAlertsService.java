package com.finvista.service;

import com.finvista.dto.FinancialAlertsResponse;
import com.finvista.dto.FinancialAlertsResponse.*;
import com.finvista.dto.DashboardAnalysisResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class FinancialAlertsService {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);
    private final ClienteContextService contexto;
    private final DashboardAnalysisService dashboard;
    private final SpendingGoalService metas;
    private final BudgetPipelineService orcamentos;
    private final BigDecimal aumentoAtencao;
    private final BigDecimal aumentoCritico;

    public FinancialAlertsService(ClienteContextService contexto,
            DashboardAnalysisService dashboard, SpendingGoalService metas,
            BudgetPipelineService orcamentos,
            @Value("${finvista.alertas.aumento-atencao:20}") BigDecimal aumentoAtencao,
            @Value("${finvista.alertas.aumento-critico:50}") BigDecimal aumentoCritico) {
        if (aumentoAtencao.signum() <= 0 || aumentoCritico.compareTo(aumentoAtencao) < 0) {
            throw new IllegalArgumentException("Os limites de aumento de despesas são inválidos.");
        }
        this.contexto = contexto;
        this.dashboard = dashboard;
        this.metas = metas;
        this.orcamentos = orcamentos;
        this.aumentoAtencao = aumentoAtencao;
        this.aumentoCritico = aumentoCritico;
    }

    @Transactional(readOnly = true)
    public FinancialAlertsResponse consultar(LocalDate inicio, LocalDate fim, String categoria) {
        return calcular(inicio, fim, categoria, 3);
    }

    @Transactional(readOnly = true)
    public FinancialAlertsResponse consultarCompleto(LocalDate inicio, LocalDate fim) {
        return calcular(inicio, fim, null, Integer.MAX_VALUE);
    }

    private FinancialAlertsResponse calcular(LocalDate inicio, LocalDate fim, String categoria, int limite) {
        Long clienteId = contexto.getClienteAtualId();
        DashboardAnalysisResponse analise = dashboard.consultar(inicio, fim, categoria);
        if (!clienteId.equals(analise.clienteId())) {
            throw new IllegalStateException("A empresa selecionada mudou. Atualize o painel.");
        }
        Map<String, Alerta> alertas = new LinkedHashMap<>();
        for (var meta : metas.listarComSituacao()) {
            if (!pertenceAoPeriodo(meta.dataInicio(), meta.dataFim(), analise)
                    || meta.valorLimite() == null || meta.valorLimite().signum() <= 0
                    || meta.gastoAtual() == null) {
                continue;
            }
            BigDecimal limiar = BigDecimal.valueOf(meta.percentualAlerta() == null ? 80 : meta.percentualAlerta());
            if (!atingiu(meta.gastoAtual(), meta.valorLimite(), limiar)) {
                continue;
            }
            boolean excedida = meta.gastoAtual().compareTo(meta.valorLimite()) > 0;
            String id = "META:" + clienteId + ":" + meta.id();
            alertas.put(id, new Alerta(id, Tipo.META_LIMITE,
                    excedida ? Gravidade.CRITICO : Gravidade.ATENCAO, meta.id(),
                    "Meta de gastos #" + meta.id(),
                    excedida ? "O consumo registrado excedeu o limite da meta."
                            : "O consumo registrado atingiu o percentual de atenção da meta.",
                    meta.dataInicio(), meta.dataFim(), meta.gastoAtual(), meta.valorLimite(),
                    meta.gastoAtual().subtract(meta.valorLimite()), percentual(meta.gastoAtual(), meta.valorLimite()),
                    limiar, null, null, meta.categoria(), meta.centroCusto(), meta.dataFim().isBefore(analise.hoje())));
        }
        for (var orcamento : orcamentos.listar()) {
            if (!pertenceAoPeriodo(orcamento.dataInicio(), orcamento.dataFim(), analise)
                    || orcamento.valorPlanejado() == null || orcamento.valorPlanejado().signum() <= 0
                    || orcamento.valorUtilizado() == null
                    || orcamento.valorUtilizado().compareTo(orcamento.valorPlanejado()) <= 0) {
                continue;
            }
            String id = "ORCAMENTO:" + clienteId + ":" + orcamento.id();
            alertas.put(id, new Alerta(id, Tipo.ORCAMENTO_EXCEDENTE, Gravidade.CRITICO, orcamento.id(),
                    orcamento.nome(), "O consumo registrado excedeu o valor planejado do orçamento.",
                    orcamento.dataInicio(), orcamento.dataFim(), orcamento.valorUtilizado(),
                    orcamento.valorPlanejado(), orcamento.valorUtilizado().subtract(orcamento.valorPlanejado()),
                    percentual(orcamento.valorUtilizado(), orcamento.valorPlanejado()), CEM, null, null,
                    orcamento.categoria(), orcamento.centroCusto(), orcamento.dataFim().isBefore(analise.hoje())));
        }
        Comparacao comparacao = comparar(analise, clienteId, alertas);
        if (!clienteId.equals(contexto.getClienteAtualId())) {
            throw new IllegalStateException("A empresa selecionada mudou. Atualize o painel.");
        }
        var prioridades = alertas.values().stream().sorted(
                Comparator.comparingInt((Alerta alerta) -> alerta.gravidade().ordinal()).reversed()
                        .thenComparing(Alerta::percentual, Comparator.reverseOrder())
                        .thenComparing(Alerta::id)).limit(limite).toList();
        return new FinancialAlertsResponse(clienteId, analise.inicio(), analise.fim(), analise.categoria(),
                alertas.size(), prioridades, comparacao);
    }

    private boolean pertenceAoPeriodo(LocalDate inicio, LocalDate fim, DashboardAnalysisResponse analise) {
        return !inicio.isAfter(analise.hoje()) && !inicio.isAfter(analise.fim()) && !fim.isBefore(analise.inicio());
    }

    private Comparacao comparar(DashboardAnalysisResponse analise, Long clienteId, Map<String, Alerta> alertas) {
        if (!analise.fim().isBefore(analise.hoje())) {
            return Comparacao.PERIODO_EM_ANDAMENTO;
        }
        var registros = analise.registros();
        if (registros == null || registros.despesasAtual() == 0 || registros.despesasAnterior() == 0) {
            return Comparacao.SEM_REGISTROS;
        }
        BigDecimal anterior = analise.indicadores().despesaMesAnterior();
        BigDecimal atual = analise.indicadores().despesa();
        if (anterior.signum() <= 0) {
            return Comparacao.BASE_NAO_POSITIVA;
        }
        BigDecimal diferenca = atual.subtract(anterior);
        if (atingiu(diferenca, anterior, aumentoAtencao)) {
            boolean critico = atingiu(diferenca, anterior, aumentoCritico);
            String id = "DESPESAS:" + clienteId + ":" + analise.inicio() + ":" + analise.fim()
                    + ":" + (analise.categoria() == null ? "TODAS" : analise.categoria());
            alertas.put(id, new Alerta(id, Tipo.AUMENTO_DESPESAS,
                    critico ? Gravidade.CRITICO : Gravidade.ATENCAO, null,
                    "Aumento das despesas registradas", "As despesas registradas aumentaram em relação ao período anterior.",
                    analise.inicio(), analise.fim(), atual, anterior, diferenca, percentual(diferenca, anterior),
                    critico ? aumentoCritico : aumentoAtencao, analise.inicioAnterior(), analise.fimAnterior(),
                    analise.categoria(), null, true));
        }
        return Comparacao.DISPONIVEL;
    }

    private boolean atingiu(BigDecimal valor, BigDecimal base, BigDecimal limiar) {
        return valor.multiply(CEM).compareTo(base.multiply(limiar)) >= 0;
    }

    private BigDecimal percentual(BigDecimal valor, BigDecimal base) {
        return valor.multiply(CEM).divide(base, 2, RoundingMode.HALF_UP);
    }
}
