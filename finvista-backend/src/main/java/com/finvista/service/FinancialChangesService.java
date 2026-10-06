package com.finvista.service;

import com.finvista.dto.FinancialChangesResponse;
import com.finvista.dto.FinancialChangesResponse.CategoryChange;
import com.finvista.model.FinancialTransaction;
import com.finvista.repository.FinancialTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FinancialChangesService {

    private final FinancialTransactionRepository repository;
    private final ClienteContextService contexto;
    private final FinancialReferenceService referencia;

    private ExpenseAllocationService expenseAllocationService;

    @Autowired
    public FinancialChangesService(FinancialTransactionRepository repository,
            ClienteContextService contexto, FinancialReferenceService referencia,
            ExpenseAllocationService allocations) {
        this(repository, contexto, referencia);
        this.expenseAllocationService = java.util.Objects.requireNonNull(allocations);
    }

    public FinancialChangesService(
            FinancialTransactionRepository repository,
            ClienteContextService contexto,
            FinancialReferenceService referencia
    ) {
        this.repository = repository;
        this.contexto = contexto;
        this.referencia = referencia;
    }

    public FinancialChangesResponse obterMudancas() {
        Long clienteId = contexto.getClienteAtualId();
        YearMonth atual = referencia.obterMesReferencia();
        YearMonth anterior = atual.minusMonths(1);
        List<FinancialTransaction> registros
                = repository.findByClienteIdAndDataBetweenOrderByDataAsc(
                        clienteId, anterior.atDay(1), atual.atEndOfMonth());

        Map<String, BigDecimal[]> totais = new HashMap<>();
        BigDecimal totalAtual = BigDecimal.ZERO;
        BigDecimal totalAnterior = BigDecimal.ZERO;
        int quantidadeAtual = 0;
        int quantidadeAnterior = 0;

        List<FinancialTransaction> despesasAtual = new ArrayList<>();
        List<FinancialTransaction> despesasAnterior = new ArrayList<>();

        // Totais e quantidades continuam contando lançamentos, não parcelas.
        for (FinancialTransaction registro : registros) {
            if (registro.getData() == null || registro.getValor() == null
                    || registro.getTipo() == null
                    || !"DESPESA".equalsIgnoreCase(registro.getTipo().trim())) {
                continue;
            }
            YearMonth mes = YearMonth.from(registro.getData());
            if (!mes.equals(atual) && !mes.equals(anterior)) {
                continue;
            }
            if (mes.equals(atual)) {
                despesasAtual.add(registro);
                totalAtual = totalAtual.add(registro.getValor());
                quantidadeAtual++;
            } else {
                despesasAnterior.add(registro);
                totalAnterior = totalAnterior.add(registro.getValor());
                quantidadeAnterior++;
            }
        }

        somarCategorias(totais, partes(clienteId, despesasAtual), 0);
        somarCategorias(totais, partes(clienteId, despesasAnterior), 1);

        List<CategoryChange> mudancas = new ArrayList<>();
        totais.forEach((categoria, valores) -> {
            BigDecimal diferenca = valores[0].subtract(valores[1]);
            if (diferenca.signum() != 0) {
                mudancas.add(new CategoryChange(
                        categoria, valores[0], valores[1], diferenca));
            }
        });
        mudancas.sort(Comparator
                .comparing((CategoryChange item) -> item.diferenca().abs())
                .reversed()
                .thenComparing(CategoryChange::categoria));

        BigDecimal diferenca = totalAtual.subtract(totalAnterior);
        // Base zero ou negativa: não apresentar uma taxa percentual.
        BigDecimal percentual = totalAnterior.signum() <= 0 ? null
                : diferenca.multiply(new BigDecimal("100"))
                        .divide(totalAnterior, 2, RoundingMode.HALF_UP);

        return new FinancialChangesResponse(
                atual.toString(), anterior.toString(),
                totalAtual, totalAnterior, diferenca, percentual,
                quantidadeAtual, quantidadeAnterior, List.copyOf(mudancas));
    }

    private void somarCategorias(Map<String, BigDecimal[]> totais,
            List<ExpenseAllocationService.Parte> parcelas, int indice) {
        for (ExpenseAllocationService.Parte parcela : parcelas) {
            BigDecimal[] valores = totais.computeIfAbsent(parcela.categoria(),
                    chave -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            valores[indice] = valores[indice].add(parcela.valor());
        }
    }

    private List<ExpenseAllocationService.Parte> partes(Long clienteId,
            List<FinancialTransaction> despesas) {
        return expenseAllocationService == null
                ? ExpenseAllocationService.legado(despesas)
                : expenseAllocationService.dividir(clienteId, despesas);
    }

    
}
