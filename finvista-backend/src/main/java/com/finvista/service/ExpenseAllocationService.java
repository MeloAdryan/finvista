package com.finvista.service;

import com.finvista.model.FinancialAllocation;
import com.finvista.model.FinancialTransaction;
import com.finvista.repository.FinancialAllocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExpenseAllocationService {

    public static final String SEM_CATEGORIA = "Sem categoria";
    public static final String SEM_CENTRO = "Sem centro de custo";
    public static final String CATEGORIA_REVISAR = "Categoria a revisar";
    public static final String CENTRO_REVISAR = "Centro a revisar";

    public record Parte(String categoria, String centroCusto, BigDecimal valor) {

    }
    public record ParteDetalhada(FinancialTransaction lancamento, Parte parte) {}

    private final FinancialAllocationRepository repository;

    public ExpenseAllocationService(FinancialAllocationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Parte> dividir(Long clienteId, List<FinancialTransaction> despesas) {
        return dividirDetalhado(clienteId, despesas).stream().map(ParteDetalhada::parte).toList();
    }

    @Transactional(readOnly = true)
    public List<ParteDetalhada> dividirDetalhado(Long clienteId, List<FinancialTransaction> despesas) {
        Objects.requireNonNull(clienteId, "Cliente obrigatório para consultar rateios.");
        List<Long> ids = despesas.stream().map(FinancialTransaction::getId)
                .filter(Objects::nonNull).distinct().toList();
        Set<Long> selecionados = new HashSet<>(ids);
        Map<Long, List<FinancialAllocation>> porLancamento = ids.isEmpty() ? Map.of()
                : repository.findByLancamentoClienteIdAndLancamentoIdInOrderByLancamentoIdAscBlocoAsc(clienteId, ids)
                        .stream().filter(r -> r.getLancamento() != null
                        && selecionados.contains(r.getLancamento().getId()))
                        .collect(Collectors.groupingBy(r -> r.getLancamento().getId()));
        List<ParteDetalhada> resultado = new ArrayList<>();
        for (FinancialTransaction despesa : despesas) {
            List<FinancialAllocation> rateios = despesa.getId() == null ? List.of()
                    : porLancamento.getOrDefault(despesa.getId(), List.of());
            if (rateios.isEmpty()) {
                resultado.add(new ParteDetalhada(despesa, parteLegada(despesa)));
                continue;
            }
            BigDecimal valor = valor(despesa);
            Set<Integer> blocos = new HashSet<>();
            boolean invalido = despesa.getValor() == null || rateios.stream().anyMatch(r
                    -> r.getValorCategoria() == null || r.getBloco() < 1 || !blocos.add(r.getBloco()));
            BigDecimal soma = invalido ? BigDecimal.ZERO : rateios.stream()
                    .map(FinancialAllocation::getValorCategoria).reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal fator = soma.compareTo(valor) == 0 ? BigDecimal.ONE
                    : soma.negate().compareTo(valor) == 0 ? BigDecimal.ONE.negate() : null;
            if (invalido || fator == null) {
                resultado.add(new ParteDetalhada(despesa, new Parte(CATEGORIA_REVISAR, CENTRO_REVISAR, valor)));
                continue;
            }
            for (FinancialAllocation r : rateios) {
                String centro = nome(r.getCentro(), SEM_CENTRO);
                if (!SEM_CENTRO.equals(centro) && (r.getValorCentro() == null
                        || r.getValorCentro().compareTo(r.getValorCategoria()) != 0)) {
                    centro = CENTRO_REVISAR;
                }
                resultado.add(new ParteDetalhada(despesa, new Parte(nome(r.getCategoria(), SEM_CATEGORIA), centro,
                        r.getValorCategoria().multiply(fator))));
            }
        }
        return List.copyOf(resultado);
    }

    public static List<Parte> legado(List<FinancialTransaction> despesas) {
        return despesas.stream().map(ExpenseAllocationService::parteLegada).toList();
    }

    private static Parte parteLegada(FinancialTransaction t) {
        return new Parte(nome(t.getCategoria(), SEM_CATEGORIA), nome(t.getCentroCusto(), SEM_CENTRO), valor(t));
    }

    private static BigDecimal valor(FinancialTransaction t) {
        return t.getValor() == null ? BigDecimal.ZERO : t.getValor();
    }

    private static String nome(String valor, String padrao) {
        return valor == null || valor.isBlank() ? padrao : valor.trim();
    }
}
