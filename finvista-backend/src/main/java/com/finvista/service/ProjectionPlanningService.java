package com.finvista.service;

import com.finvista.dto.ProjectionPlanningResponse;
import com.finvista.dto.ProjectionPlanningResponse.Mes;
import com.finvista.service.FinancialTransactionAggregationService.MonthlyFinancialSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

@Service
public class ProjectionPlanningService {

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final BigDecimal VALOR_MAXIMO = new BigDecimal("9999999999999.99");
    private final FinancialTransactionAggregationService aggregation;
    private final Clock clock;

    public ProjectionPlanningService(FinancialTransactionAggregationService aggregation, Clock clock) {
        this.aggregation = aggregation;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProjectionPlanningResponse simular(BigDecimal receitaMensal, BigDecimal saldoInicial, BigDecimal variacao) {
        if (receitaMensal != null) {
            validarDinheiro(receitaMensal, false, "Receita mensal");
        }
        BigDecimal inicial = saldoInicial == null ? BigDecimal.ZERO : saldoInicial;
        validarDinheiro(inicial, true, "Saldo inicial");
        BigDecimal percentual = variacao == null ? new BigDecimal("20") : variacao;
        if (percentual.signum() < 0 || percentual.compareTo(CEM) > 0 || percentual.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("A variação deve estar entre 0 e 100%, com até duas casas decimais.");
        }

        LocalDate hoje = LocalDate.now(clock.withZone(ZoneId.of("America/Sao_Paulo")));
        YearMonth referencia = YearMonth.from(hoje);
        LocalDate inicio = referencia.atDay(1), fim = referencia.plusMonths(5).atEndOfMonth();
        Map<YearMonth, MonthlyFinancialSummary> registros = new HashMap<>();
        // O serviço agregado resolve o cliente autenticado; nenhum cliente vem da requisição.
        for (var resumo : aggregation.obterResumoMensal(referencia.minusMonths(3).atDay(1), fim)) {
            registros.put(resumo.periodo(), resumo);
        }
        BigDecimal receitasAnteriores = BigDecimal.ZERO;
        for (int i = 1; i <= 3; i++) {
            var anterior = registros.get(referencia.minusMonths(i));
            if (anterior != null) {
                receitasAnteriores = receitasAnteriores.add(zero(anterior.receita()));
            }
        }
        BigDecimal media = receitasAnteriores.divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);
        BigDecimal esperada = receitaMensal == null ? media.max(BigDecimal.ZERO) : receitaMensal;
        BigDecimal alvoConservador = esperada.multiply(CEM.subtract(percentual)).divide(CEM, 2, RoundingMode.HALF_UP);
        BigDecimal alvoOtimista = esperada.multiply(CEM.add(percentual)).divide(CEM, 2, RoundingMode.HALF_UP);
        BigDecimal acumulado = BigDecimal.ZERO, base = inicial, conservador = inicial, otimista = inicial;
        List<Mes> meses = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            YearMonth periodo = referencia.plusMonths(i);
            var registro = registros.get(periodo);
            BigDecimal receita = registro == null ? BigDecimal.ZERO : zero(registro.receita());
            BigDecimal despesa = registro == null ? BigDecimal.ZERO : zero(registro.despesa());
            // A receita esperada é um alvo total, não uma soma adicional à receita cadastrada.
            BigDecimal simulada = receitaTotal(receita, esperada);
            BigDecimal complementar = simulada.subtract(receita);
            BigDecimal resultado = simulada.subtract(despesa);
            acumulado = acumulado.add(resultado);
            base = base.add(resultado);
            conservador = conservador.add(receitaTotal(receita, alvoConservador).subtract(despesa));
            otimista = otimista.add(receitaTotal(receita, alvoOtimista).subtract(despesa));
            meses.add(new Mes(periodo.toString(), receita, complementar, simulada, despesa,
                    resultado, acumulado, base, conservador, otimista));
        }
        return new ProjectionPlanningResponse(inicio, fim, hoje, media, 3, esperada,
                inicial, percentual, List.copyOf(meses));
    }

    private BigDecimal receitaTotal(BigDecimal registrada, BigDecimal alvo) {
        // Alvo zero significa não projetar complementos, preservando inclusive estornos.
        return alvo.signum() == 0 ? registrada : registrada.max(alvo);
    }

    private BigDecimal zero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private void validarDinheiro(BigDecimal valor, boolean permiteNegativo, String nome) {
        if ((!permiteNegativo && valor.signum() < 0) || valor.abs().compareTo(VALOR_MAXIMO) > 0
                || valor.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(nome + " inválido: utilize até duas casas decimais e um valor dentro do limite permitido.");
        }
    }
}
