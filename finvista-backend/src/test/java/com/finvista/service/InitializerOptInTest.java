package com.finvista.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import static org.junit.jupiter.api.Assertions.*;

class InitializerOptInTest {

    @Test
    void criacaoInicialDependeDeHabilitacaoExplicita() {
        for (var type : new Class<?>[]{com.finvista.config.AdminInitializer.class, com.finvista.config.ClienteInitializer.class}) {
            var condition = type.getAnnotation(ConditionalOnProperty.class);
            assertNotNull(condition);
            assertArrayEquals(new String[]{"finvista.seed.enabled"}, condition.name());
            assertEquals("true", condition.havingValue());
            assertFalse(condition.matchIfMissing());
        }
    }
}
