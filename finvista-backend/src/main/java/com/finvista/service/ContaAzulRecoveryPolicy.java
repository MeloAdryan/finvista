package com.finvista.service;

import com.finvista.model.FinancialTransaction;
import com.finvista.dto.importacao.ContaAzulSyncResponse.CampoRecuperacao;
import java.math.BigDecimal;
import java.util.*;

final class ContaAzulRecoveryPolicy {
    private ContaAzulRecoveryPolicy() {}
    static Map<String,BigDecimal> financeiros(FinancialTransaction t) {
        Map<String,BigDecimal> m=new LinkedHashMap<>();
        m.put("valorOriginal",t.getValorOriginal());m.put("valorRealizado",t.getValorRealizado());
        m.put("valorAberto",t.getValorAberto());m.put("valorTotalRealizado",t.getValorTotalRealizado());
        m.put("valorTotalAberto",t.getValorTotalAberto());m.put("jurosRealizado",t.getJurosRealizado());
        m.put("multaRealizada",t.getMultaRealizada());m.put("descontoRealizado",t.getDescontoRealizado());
        m.put("jurosPrevisto",t.getJurosPrevisto());m.put("multaPrevista",t.getMultaPrevista());
        m.put("descontoPrevisto",t.getDescontoPrevisto());return m;
    }
    static boolean divergente(FinancialTransaction atual,FinancialTransaction fonte) {
        var a=financeiros(atual);var b=financeiros(fonte);
        if(atual.getValor()==null || fonte.getValor()==null || atual.getValor().compareTo(fonte.getValor())!=0) return true;
        return a.entrySet().stream().anyMatch(e -> e.getValue()!=null && b.get(e.getKey())!=null
                && e.getValue().compareTo(b.get(e.getKey()))!=0);
    }
    static List<CampoRecuperacao> campos(FinancialTransaction atual,FinancialTransaction fonte) {
        var a=financeiros(atual);
        return financeiros(fonte).entrySet().stream().filter(e -> a.get(e.getKey())==null && e.getValue()!=null)
                .map(e -> new CampoRecuperacao(e.getKey(),e.getValue())).toList();
    }
    static void preencher(FinancialTransaction t,List<CampoRecuperacao> campos) {
        for(var c:campos) {
            // Lista fechada: nenhum campo contábil, data, metadado ou rateio é alterado.
            switch(c.campo()) {
                case "valorOriginal" -> t.setValorOriginal(c.valor());
                case "valorRealizado" -> t.setValorRealizado(c.valor());
                case "valorAberto" -> t.setValorAberto(c.valor());
                case "valorTotalRealizado" -> t.setValorTotalRealizado(c.valor());
                case "valorTotalAberto" -> t.setValorTotalAberto(c.valor());
                case "jurosRealizado" -> t.setJurosRealizado(c.valor());
                case "multaRealizada" -> t.setMultaRealizada(c.valor());
                case "descontoRealizado" -> t.setDescontoRealizado(c.valor());
                case "jurosPrevisto" -> t.setJurosPrevisto(c.valor());
                case "multaPrevista" -> t.setMultaPrevista(c.valor());
                case "descontoPrevisto" -> t.setDescontoPrevisto(c.valor());
                default -> throw new IllegalArgumentException("Campo não permitido na recuperação.");
            }
        }
    }
}
