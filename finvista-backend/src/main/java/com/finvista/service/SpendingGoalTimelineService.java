package com.finvista.service;

import com.finvista.dto.SpendingGoalTimelineResponse;
import com.finvista.dto.SpendingGoalTimelineResponse.*;
import com.finvista.model.SpendingGoal;
import com.finvista.model.FinancialTransaction;
import com.finvista.repository.FinancialTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class SpendingGoalTimelineService {

    private final SpendingGoalService goals;
    private final FinancialTransactionRepository transactions;
    private final ExpenseAllocationService allocations;
    private final Clock clock;

    public SpendingGoalTimelineService(SpendingGoalService goals, FinancialTransactionRepository transactions,
            ExpenseAllocationService allocations, Clock clock) {
        this.goals = goals;
        this.transactions = transactions;
        this.allocations = allocations;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SpendingGoalTimelineResponse consultar(Long id) {
        SpendingGoal meta = goals.buscarPorId(id); // busca por id E cliente autenticado.
        LocalDate inicio = meta.getDataInicio(), fim = meta.getDataFim();
        long quantidade = ChronoUnit.DAYS.between(inicio, fim) + 1;
        if (quantidade < 1 || quantidade > 366) {
            throw new IllegalArgumentException("Período da meta inválido para o acompanhamento diário.");
        }
        int dias = (int) quantidade;
        LocalDate hoje = LocalDate.now(clock.withZone(ZoneId.of("America/Sao_Paulo")));
        String situacao = hoje.isBefore(inicio) ? "FUTURA" : hoje.isAfter(fim) ? "ENCERRADA" : "EM_ANDAMENTO";
        int decorridos = (int) Math.max(0, Math.min(dias, ChronoUnit.DAYS.between(inicio, hoje) + 1));
        var registros = transactions.findByClienteIdAndTipoAndDataBetweenOrderByDataAsc(meta.getCliente().getId(), "DESPESA", inicio, fim)
                .stream().filter(t -> t.getData() != null && !t.getData().isBefore(inicio) && !t.getData().isAfter(fim)
                        && t.getValor() != null && t.getTipo() != null && "DESPESA".equalsIgnoreCase(t.getTipo().trim())).toList();
        Map<LocalDate, BigDecimal> diarios = new TreeMap<>();
        Map<String, BigDecimal> categorias = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        BigDecimal total = BigDecimal.ZERO, ateHoje = BigDecimal.ZERO;
        for (var detalhada : allocations.dividirDetalhado(meta.getCliente().getId(), registros)) {
            var parte = detalhada.parte();
            if (!corresponde(meta.getCategoria(), parte.categoria()) || !corresponde(meta.getCentroCusto(), parte.centroCusto())) {
                continue;
            }
            LocalDate data = detalhada.lancamento().getData();
            total = total.add(parte.valor());
            if (!data.isAfter(hoje)) {
                ateHoje = ateHoje.add(parte.valor());
            }
            diarios.merge(data, parte.valor(), BigDecimal::add);
            categorias.merge(parte.categoria(), parte.valor(), BigDecimal::add);
        }
        BigDecimal estimativa = "EM_ANDAMENTO".equals(situacao) && decorridos > 0 && ateHoje.signum() >= 0
                ? ateHoje.multiply(BigDecimal.valueOf(dias)).divide(BigDecimal.valueOf(decorridos), 2, RoundingMode.HALF_UP) : null;
        BigDecimal ritmo = BigDecimal.valueOf(decorridos).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(dias), 2, RoundingMode.HALF_UP);
        List<Ponto> pontos = new ArrayList<>();
        BigDecimal acumulado = BigDecimal.ZERO;
        for (int i = 0; i < dias; i++) {
            LocalDate data = inicio.plusDays(i);
            acumulado = acumulado.add(diarios.getOrDefault(data, BigDecimal.ZERO));
            BigDecimal ideal = meta.getValorLimite().multiply(BigDecimal.valueOf(i + 1)).divide(BigDecimal.valueOf(dias), 2, RoundingMode.HALF_UP);
            BigDecimal linhaEstimada = null;
            if (estimativa != null && !data.isBefore(hoje)) {
                linhaEstimada = data.equals(hoje) ? ateHoje
                        : ateHoje.multiply(BigDecimal.valueOf(i + 1)).divide(BigDecimal.valueOf(decorridos), 2, RoundingMode.HALF_UP);
            }
            pontos.add(new Ponto(data, data.isAfter(hoje) ? null : acumulado, ideal, linhaEstimada));
        }
        return new SpendingGoalTimelineResponse(id, inicio, fim, hoje, situacao, dias, decorridos, meta.getValorLimite(), total, ateHoje, total.subtract(ateHoje), ritmo, estimativa, List.copyOf(pontos),
                categorias.entrySet().stream().map(e -> new Categoria(e.getKey(), e.getValue())).sorted(Comparator.comparing((Categoria c) -> c.valor().abs()).reversed()).toList());
    }

    private boolean corresponde(String criterio, String valor) {
        return criterio == null || criterio.isBlank() || criterio.trim().equalsIgnoreCase(valor);
    }
}
