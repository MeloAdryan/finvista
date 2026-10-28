package com.finvista.integration.contaazul;

import java.time.LocalDate;

/**
 * Default acquisition policy for new full synchronizations. Financial views
 * may still display older records already held by the company.
 */
public final class ContaAzulFullScope {

    public static final LocalDate INICIO = LocalDate.of(2000, 1, 1);
    public static final LocalDate FIM = LocalDate.of(2200, 12, 31);

    private ContaAzulFullScope() {
    }
}
