package com.finvista.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvista.integration.contaazul.*;
import com.finvista.model.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public final class AuditedSettlementReader {

    private AuditedSettlementReader() {
    }

    public record Payment(String id, LocalDate data, BigDecimal principal, BigDecimal liquido) {}

    public record Settlement(ContaAzulParcelDetail parcela, List<Payment> pagamentos) {}

    private static boolean same(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) == 0;
    }

    public static Settlement read(FinancialTransaction t, ContaAzulApiOperation op, boolean ambiguous, ObjectMapper json, LocalDate today) {
        try {
            if (op == null || ambiguous || !"CONTA_AZUL_API".equals(t.getOrigem())) {
                throw new IllegalArgumentException();
            }
            var p = json.readValue(op.getOrigemSnapshot(), ContaAzulParcelDetail.class);
            if (p.evento() == null || p.id() == null || !p.id().equalsIgnoreCase(t.getDocumentoReferencia()) || !Objects.equals(p.evento().tipo(), t.getTipo()) || !Objects.equals(p.status(), t.getSituacao()) || !Objects.equals(p.evento().competencia(), t.getDataCompetencia())) {
                throw new IllegalArgumentException();
            }
            var v = ContaAzulSettlementPolicy.conferir(p);
            if (!same(t.getValor(), v.original()) || !same(t.getValorRealizado(), v.principalPago()) || !same(t.getValorTotalRealizado(), v.totalPago()) || !same(t.getValorTotalAberto(), v.totalAberto())) {
                throw new IllegalArgumentException();
            }
            List<Payment> rows = new ArrayList<>();
            for (var q : p.baixas() == null ? List.<ContaAzulParcelDetail.Baixa>of() : p.baixas()) {
                if (q.data().isAfter(today) || q.data().isBefore(ContaAzulFullScope.INICIO)) {
                    throw new IllegalArgumentException();
                }
                var c = q.composicao();
                rows.add(new Payment(q.id(), q.data(), c.bruto(), c.bruto().add(c.juros()).add(c.multa()).subtract(c.desconto())));
            }
            return new Settlement(p, List.copyOf(rows));
        } catch (Exception e) {
            throw new IllegalArgumentException("Sem baixas auditadas utilizáveis", e);
        }
    }
}
