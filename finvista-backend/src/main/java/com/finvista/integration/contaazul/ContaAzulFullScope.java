package com.finvista.integration.contaazul;

import java.time.LocalDate;

/**
 * Default acquisition policy for new full synchronizations. Financial views
 * may still display older records already held by the company.
 */
public final class ContaAzulFullScope {

    public static final LocalDate INICIO = LocalDate.of(2000, 1, 1);
    public static LocalDate fim(java.time.Clock clock) {
        return LocalDate.now(clock.withZone(java.time.ZoneId.of("America/Sao_Paulo"))).plusYears(5);
    }

    private ContaAzulFullScope() {
    }
}
