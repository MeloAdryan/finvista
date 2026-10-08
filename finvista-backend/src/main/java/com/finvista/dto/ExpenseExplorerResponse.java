package com.finvista.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExpenseExplorerResponse(
        LocalDate inicio, LocalDate fim, LocalDate inicioAnterior, LocalDate fimAnterior,
        String centroCusto, String categoria, String comportamento, String natureza,
        BigDecimal total, BigDecimal totalAnterior, List<String> centrosDisponiveis,
        List<String> categoriasDisponiveis, List<Linha> centros, List<Linha> categorias,
        List<Item> itens
        ) {

    public record Linha(String nome, BigDecimal atual, BigDecimal anterior, BigDecimal diferenca) {}

    public record Item(Long id, LocalDate data, String descricao, BigDecimal valorSelecionado, BigDecimal valorIntegral) {}
}
