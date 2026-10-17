package com.finvista.model;

import com.finvista.dto.FinancialAlertsResponse.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class FinancialAlertRecordTest {

    private final Instant agora = Instant.parse("2026-10-11T12:00:00Z");

    private Alerta alerta(Gravidade gravidade) {
        return new Alerta("META:1:1", Tipo.META_LIMITE, gravidade, 1L, "Meta", "Aviso",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN, null, null, null, null, true);
    }

    @Test
    void mudancaDeValorNaoReabreLeituraSemAgravamento() {
        var registro = new FinancialAlertRecord(new Cliente("Teste"), alerta(Gravidade.ATENCAO), "original", agora);
        registro.atualizar(alerta(Gravidade.ATENCAO), "valor atualizado", agora.plusSeconds(60));
        assertEquals(agora, registro.getNotificadoEm());
        assertEquals(agora.plusSeconds(60), registro.getAlteradoEm());
        assertEquals(1, registro.getOcorrencias());
    }

    @Test
    void reavaliacaoIgualAtualizaSoVerificacao() {
        var registro = new FinancialAlertRecord(new Cliente("Teste"), alerta(Gravidade.ATENCAO), "original", agora);
        registro.atualizar(alerta(Gravidade.ATENCAO), "original", agora.plusSeconds(60));
        assertEquals(agora, registro.getAlteradoEm());
        assertEquals(agora.plusSeconds(60), registro.getVerificadoEm());
    }

    @Test
    void resolucaoPreservaEvidenciaEDataInicial() {
        var registro = new FinancialAlertRecord(new Cliente("Teste"), alerta(Gravidade.ATENCAO), "evidência", agora);
        registro.resolver(agora.plusSeconds(60));
        registro.resolver(agora.plusSeconds(120));
        assertEquals("evidência", registro.getDados());
        assertEquals(agora.plusSeconds(60), registro.getResolvidoEm());
        assertEquals(agora, registro.getCriadoEm());
    }
}
