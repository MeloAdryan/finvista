package com.finvista.service;

import com.finvista.dto.DashboardAnalysisResponse.Recebimentos;
import com.finvista.model.FinancialTransaction;
import java.math.BigDecimal;
import java.util.Locale;

/** Situação atual do principal, agrupada pela competência; não é histórico de baixas. */
final class ReceiptComposition {
    private BigDecimal recebido = BigDecimal.ZERO;
    private BigDecimal aberto = BigDecimal.ZERO;
    private BigDecimal desconhecido = BigDecimal.ZERO;
    private int incompletos;

    void adicionar(FinancialTransaction t, BigDecimal selecionado) {
        BigDecimal pago = t.getValorRealizado();
        BigDecimal saldo = t.getValorAberto();
        String situacao = t.getSituacao() == null ? "" : t.getSituacao().trim().toUpperCase(Locale.ROOT);
        // Sem inferir saldos a partir do valor contábil nem ratear baixas entre categorias.
        boolean completo = t.getValor() != null && selecionado.compareTo(t.getValor()) == 0
                && selecionado.signum() >= 0 && pago != null && saldo != null
                && pago.signum() >= 0 && saldo.signum() >= 0
                && pago.add(saldo).compareTo(selecionado) == 0
                && !situacao.startsWith("CANCELAD");
        if (completo) {
            recebido = recebido.add(pago);
            aberto = aberto.add(saldo);
        } else {
            desconhecido = desconhecido.add(selecionado);
            incompletos++;
        }
    }

    Recebimentos resumo() {
        return new Recebimentos(recebido, aberto, desconhecido, incompletos);
    }
}
