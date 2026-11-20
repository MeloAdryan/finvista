package com.finvista.integration.contaazul;

import com.finvista.model.*;
import com.finvista.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ContaAzulRunAudit {

    public record Linha(String mes, String tipo, long quantidadeApi, long quantidadeBanco,
            BigDecimal totalApi, BigDecimal totalBanco, BigDecimal pagoApi, BigDecimal pagoBanco,
            BigDecimal abertoApi, BigDecimal abertoBanco) {}

    private static class Soma {

        long a, b;
        BigDecimal at = BigDecimal.ZERO, bt = BigDecimal.ZERO, ap = BigDecimal.ZERO, bp = BigDecimal.ZERO, aa = BigDecimal.ZERO, ba = BigDecimal.ZERO;
    }
    private final ContaAzulAutoItemRepository itens;
    private final FinancialTransactionRepository transacoes;
    private final ObjectMapper json;

    public ContaAzulRunAudit(ContaAzulAutoItemRepository itens, FinancialTransactionRepository transacoes, ObjectMapper json) {
        this.itens = itens;
        this.transacoes = transacoes;
        this.json = json;
    }

    public void conferir(ContaAzulAutoRun r) {
        // A delta listing cannot prove the coverage of a whole month.
        if (Boolean.TRUE.equals(r.incremental)) {
            r.conferenciaStatus = "ESCOPO_INCREMENTAL";
            return;
        }
        if (r.listagemMudou || !"CONCLUIDA".equals(r.status)) {
            r.conferenciaStatus = "INCOMPLETA";
            return;
        }
        var linhas = itens.findByExecucaoIdAndClienteIdAndListadaTrue(r.id, r.clienteId);
        var banco = transacoes.paraConferencia(r.clienteId, r.inicio, r.fim);
        var resultado = avaliar(linhas, banco, json);
        r.conferenciaStatus = resultado.status();
        r.conferidoEm = r.concluidoEm;
        try {
            r.conferenciaJson = json.writeValueAsString(resultado.linhas());
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível registrar a conferência.");
        }
    }

    public record Resultado(String status, List<Linha> linhas) {}

    static Resultado avaliar(List<ContaAzulAutoItem> itens, List<FinancialTransaction> banco, ObjectMapper json) {
        var grupos = new TreeMap<String, Soma>();
        var apiIds = new HashSet<String>();
        var dbIds = new HashSet<String>();
        boolean completo = true, iguais = true;
        for (var i : itens) {
            try {
                var p = json.readValue(i.resumoJson, ContaAzulFinancialClient.Parcela.class);
                if (p == null || p.id() == null || !p.id().equalsIgnoreCase(i.parcelaId) || p.vencimento() == null
                        || p.total() == null || p.pago() == null || p.pendente() == null
                        || !apiIds.add(p.id().toLowerCase(Locale.ROOT))) {
                    completo = false;
                    continue;
                }
                var s = grupos.computeIfAbsent(YearMonth.from(p.vencimento()) + "|" + i.tipo, k -> new Soma());
                s.a++;
                s.at = s.at.add(p.total());
                s.ap = s.ap.add(p.pago());
                s.aa = s.aa.add(p.pendente());
                // Cases such as loss/renegotiation are not inferred from principal.
                if (p.total().compareTo(p.pago().add(p.pendente())) != 0) {
                    completo = false;
                }
            } catch (Exception e) {
                completo = false;
            }
        }
        for (var t : banco) {
            String ref = t.getDocumentoReferencia();
            if (ref == null || ref.isBlank() || !dbIds.add(ref.toLowerCase(Locale.ROOT))) {
                completo = false;
            }
            if (t.getDataVencimento() == null || t.getValorTotalRealizado() == null || t.getValorTotalAberto() == null) {
                completo = false;
                continue;
            }
            var s = grupos.computeIfAbsent(YearMonth.from(t.getDataVencimento()) + "|" + t.getTipo(), k -> new Soma());
            s.b++;
            s.bp = s.bp.add(t.getValorTotalRealizado());
            s.ba = s.ba.add(t.getValorTotalAberto());
            s.bt = s.bt.add(t.getValorTotalRealizado().add(t.getValorTotalAberto()));
        }
        iguais = apiIds.equals(dbIds);
        var saida = new ArrayList<Linha>();
        for (var e : grupos.entrySet()) {
            var s = e.getValue();
            String[] k = e.getKey().split("\\|");
            iguais &= s.a == s.b && s.at.compareTo(s.bt) == 0 && s.ap.compareTo(s.bp) == 0 && s.aa.compareTo(s.ba) == 0;
            saida.add(new Linha(k[0], k[1], s.a, s.b, s.at, s.bt, s.ap, s.bp, s.aa, s.ba));
        }
        return new Resultado(!completo ? "NAO_VERIFICAVEL" : iguais ? "CONFERIDO" : "DIVERGENTE", List.copyOf(saida));
    }
}
