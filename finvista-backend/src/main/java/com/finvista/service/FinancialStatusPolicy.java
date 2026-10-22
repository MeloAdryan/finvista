package com.finvista.service;

import com.finvista.model.FinancialTransaction;
import java.util.Locale;
import java.util.Set;


public final class FinancialStatusPolicy {
    private FinancialStatusPolicy() {}
    public static boolean cancelado(String status) {
        return status != null && Set.of("CANCELADO", "CANCELADA").contains(status.trim().toUpperCase(Locale.ROOT));
    }
    public static boolean ativo(FinancialTransaction transaction) {
        return transaction != null && !cancelado(transaction.getSituacao());
    }
}
