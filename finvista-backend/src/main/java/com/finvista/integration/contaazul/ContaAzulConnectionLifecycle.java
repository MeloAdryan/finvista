package com.finvista.integration.contaazul;

import com.finvista.model.ContaAzulConnection;
import com.finvista.repository.ContaAzulAutoConfigRepository;
import java.time.Instant;

final class ContaAzulConnectionLifecycle {

    private ContaAzulConnectionLifecycle() {
    }

    static void reautorizar(ContaAzulConnection c, ContaAzulAutoConfigRepository configs, Instant now) {
        c.reautorizar(now);
        if (configs != null) {
            configs.bloquear(c.getClienteId()).ifPresent(config -> {
                config.habilitada = false;
                config.proximaEm = null;
                config.dono = null;
                config.reservaAte = null;
            });
        }
    }
}
