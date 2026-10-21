package com.finvista.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DashboardAnalysisResponse(
        Long clienteId, LocalDate inicio, LocalDate fim, LocalDate inicioAnterior, LocalDate fimAnterior,
        LocalDate hoje, String categoria, DashboardResponse indicadores, List<String> categorias,
        List<Mes> meses, List<Variacao> variacoes, List<Item> itens, Registros registros) {

    public record Registros(int receitasAtual, int despesasAtual, int receitasAnterior, int despesasAnterior) {}

    public record Mes(String periodo, BigDecimal receita, BigDecimal despesa, BigDecimal resultado,
            boolean emAndamento, Recebimentos recebimentos) {
        public Mes(String periodo, BigDecimal receita, BigDecimal despesa, BigDecimal resultado, boolean emAndamento) {
            this(periodo, receita, despesa, resultado, emAndamento, null);
        }
    }

    public record Recebimentos(BigDecimal recebido, BigDecimal emAberto, BigDecimal semDetalhamento,
            int registrosSemDetalhamento) {}

    public record Variacao(String categoria, BigDecimal anterior, BigDecimal atual, BigDecimal diferenca) {}

    public record Item(Long id, LocalDate data, String descricao, String tipo, BigDecimal valorSelecionado,
            BigDecimal valorContabilizadoIntegral, List<String> categorias) {}
}
