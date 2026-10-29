package com.finvista.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvista.model.*;
import com.finvista.repository.*;
import com.finvista.integration.contaazul.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.test.util.ReflectionTestUtils;

class RealizedCashFlowServiceTest {

    final FinancialTransactionRepository tx = mock(FinancialTransactionRepository.class);
    final ContaAzulApiOperationRepository ops = mock(ContaAzulApiOperationRepository.class);
    final ClienteContextService ctx = mock(ClienteContextService.class);
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    final Clock clock = Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZoneOffset.UTC);
    final RealizedCashFlowService service = new RealizedCashFlowService(tx, ops, ctx, json, clock);
    final String id = "9986f173-f531-4660-96ae-04b71c879264";
    final BigDecimal zero = new BigDecimal("0.00");
    FinancialTransaction item;

    @BeforeEach
    void setup() {
        when(ctx.getClienteAtualId()).thenReturn(7L);
        item = new FinancialTransaction();
        ReflectionTestUtils.setField(item, "id", 1L);
        item.setOrigem("CONTA_AZUL_API");
        item.setDocumentoReferencia(id);
        item.setTipo("RECEITA");
        item.setSituacao("QUITADO");
        item.setDataCompetencia(LocalDate.parse("2026-08-01"));
        item.setData(item.getDataCompetencia());
        item.setValor(new BigDecimal("100.00"));
        item.setValorRealizado(new BigDecimal("100.00"));
        item.setValorTotalRealizado(new BigDecimal("102.00"));
        item.setValorTotalAberto(zero);
        when(tx.findByClienteId(7L)).thenReturn(List.of(item));
    }

    ContaAzulParcelDetail.Composicao composition(String principal, String juros) {
        var b = new BigDecimal(principal);
        var j = new BigDecimal(juros);
        return new ContaAzulParcelDetail.Composicao(b, b.add(j), j, zero, zero, zero);
    }

    ContaAzulParcelDetail source(List<ContaAzulParcelDetail.Baixa> baixas) {
        return new ContaAzulParcelDetail(id, "Teste", "QUITADO", LocalDate.parse("2027-01-05"), new BigDecimal("102.00"), new BigDecimal("102.00"), zero,
                composition("100.00", "2.00"), new ContaAzulParcelDetail.Evento("evento", "RECEITA", item.getDataCompetencia(), new ContaAzulParcelDetail.Condicao(1), List.of()), null, null, baixas);
    }

    List<ContaAzulParcelDetail.Baixa> baixas() {
        return List.of(
                new ContaAzulParcelDetail.Baixa("primeira", id, LocalDate.parse("2026-09-10"), composition("40.00", "0.00")),
                new ContaAzulParcelDetail.Baixa("segunda", id, LocalDate.parse("2026-10-10"), composition("60.00", "2.00")));
    }

    void snapshot(ContaAzulParcelDetail p) throws Exception {
        var o = new ContaAzulApiOperation(UUID.randomUUID(), new Cliente("Teste"), 3L, id, "INCLUIR", 1L, clock.instant(), null, "{}", json.writeValueAsString(p));
        when(ops.snapshotsAtuais(7L)).thenReturn(List.of(o));
    }

    @Test
    void separaBaixasPorMesComJurosSemUsarCompetenciaOuVencimento() throws Exception {
        snapshot(source(baixas()));
        var r = service.consultar("6");
        assertFalse(r.parcial());
        assertEquals(2, r.baixasNoPeriodo());
        assertEquals(new BigDecimal("40.00"), r.meses().stream().filter(m -> m.mes().equals("2026-09")).findFirst().orElseThrow().entradas());
        assertEquals(new BigDecimal("62.00"), r.meses().stream().filter(m -> m.mes().equals("2026-10")).findFirst().orElseThrow().entradas());
        assertEquals(0, r.meses().stream().filter(m -> m.mes().equals("2026-08")).findFirst().orElseThrow().entradas().signum());
    }

    @Test
    void faltaDeSnapshotNaoViraPagamentoNaUltimaData() {
        item.setDataRealizacao(LocalDate.parse("2026-10-10"));
        when(ops.snapshotsAtuais(7L)).thenReturn(List.of());
        var r = service.consultar("6");
        assertTrue(r.parcial());
        assertEquals(1, r.lancamentosSemDetalhamento());
        assertEquals(0, r.baixasNoPeriodo());
    }

    @Test
    void baixasDuplicadasInvalidamParcelaInteira() throws Exception {
        snapshot(source(List.of(baixas().get(0), baixas().get(0))));
        var r = service.consultar("6");
        assertTrue(r.parcial());
        assertEquals(0, r.baixasNoPeriodo());
    }

    @Test
    void snapshotDivergenteNaoEntraComoCaixaConhecido() throws Exception {
        snapshot(source(baixas()));
        item.setValorTotalRealizado(new BigDecimal("99.00"));
        assertTrue(service.consultar("6").parcial());
    }

    @Test
    void cancelamentoNaoInventaEstornoDeCaixa() throws Exception {
        snapshot(source(baixas()));
        item.setSituacao("CANCELADO");
        var r = service.consultar("6");
        assertTrue(r.parcial());
        assertEquals(0, r.baixasNoPeriodo());
    }

    @Test
    void baixaFuturaNaoEhPagamentoEfetuado() throws Exception {
        var b = new ArrayList<>(baixas());
        b.set(1, new ContaAzulParcelDetail.Baixa("segunda", id, LocalDate.parse("2026-11-10"), composition("60.00", "2.00")));
        snapshot(source(b));
        var r = service.consultar("6");
        assertTrue(r.parcial());
        assertEquals(1, r.baixasNoPeriodo());
        assertEquals(new BigDecimal("40.00"), r.meses().stream().filter(m -> m.mes().equals("2026-09")).findFirst().orElseThrow().entradas());
        assertEquals(0, r.meses().stream().filter(m -> m.mes().equals("2026-10")).findFirst().orElseThrow().entradas().signum());
    }

    @Test
    void mantemSaldoAnteriorAoIntervaloSemSaldoBancarioInventado() throws Exception {
        snapshot(source(baixas()));
        var r = service.consultar("1");
        assertEquals(new BigDecimal("40.00"), r.meses().get(0).saldoInicial());
        assertEquals(new BigDecimal("102.00"), r.meses().get(0).saldoFinal());
    }

    @Test
    void validaPeriodo() {
        assertThrows(IllegalArgumentException.class, () -> service.consultar("0"));
        assertThrows(IllegalArgumentException.class, () -> service.consultar("121"));
    }
}
