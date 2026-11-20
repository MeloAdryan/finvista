package com.finvista.integration.contaazul;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ContaAzulFullScopeTest {
    @Test void usaCalendarioDeBrasiliaEInclui2028() {
        var clock = Clock.fixed(Instant.parse("2026-11-18T01:00:00Z"), ZoneOffset.UTC);
        assertEquals(LocalDate.of(2031, 11, 17), ContaAzulFullScope.fim(clock));
    }
    @Test void aniversarioDeDiaBissextoMantemDataValida() {
        var clock = Clock.fixed(Instant.parse("2028-02-29T12:00:00Z"), ZoneOffset.UTC);
        assertEquals(LocalDate.of(2033, 2, 28), ContaAzulFullScope.fim(clock));
    }
}