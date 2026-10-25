package com.finvista.integration.contaazul;

import java.time.LocalDate;

/**
 * All dates currently supported by FinVista's financial views, not a dashboard
 * filter.
 */
public final class ContaAzulFullScope {

    public static final LocalDate INICIO = LocalDate.of(1900, 1, 1);
    public static final LocalDate FIM = LocalDate.of(2200, 12, 31);

    private ContaAzulFullScope() {
    }
}
