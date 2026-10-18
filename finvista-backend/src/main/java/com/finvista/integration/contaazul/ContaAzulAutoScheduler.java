package com.finvista.integration.contaazul;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.*;

@Configuration
@EnableScheduling
public class ContaAzulAutoScheduler {

    private static final Logger log = LoggerFactory.getLogger(ContaAzulAutoScheduler.class);
    private final ContaAzulAutoState estado;
    private final ContaAzulAutoWorker worker;
    private final boolean habilitado;

    public ContaAzulAutoScheduler(ContaAzulAutoState estado, ContaAzulAutoWorker worker,
            @Value("${CONTA_AZUL_AUTO_ENABLED:false}") boolean habilitado) {
        this.estado = estado;
        this.worker = worker;
        this.habilitado = habilitado;
    }

    @Scheduled(fixedDelayString = "${CONTA_AZUL_AUTO_TICK_MS:60000}", initialDelayString = "${CONTA_AZUL_AUTO_INITIAL_MS:30000}")
    public void processar() {
        if (!habilitado) {
            return;
        }
        try {
            for (Long cliente : estado.disponiveis()) {
                try {
                    worker.executar(cliente);
                } catch (Exception e) {
                    log.warn("Sincronização automática temporariamente indisponível para cliente {}.", cliente);
                }
            }
        } catch (Exception e) {
            log.warn("Não foi possível consultar a fila de sincronização automática. Confira a migração do banco e a disponibilidade do serviço.");
        }
    }
}
