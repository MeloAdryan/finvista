package com.finvista.service;

import com.finvista.model.FinancialTransaction;
import com.finvista.repository.FinancialTransactionRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialPositionServiceTest {

    

          private final LocalDate hoje = LocalDate.of(2026, 10, 19);

        private FinancialTransaction item(String tipo, LocalDate dia, String aberto) {
            var t = new FinancialTransaction();
            t.setTipo(tipo);
            t.setDataVencimento(dia);
            if (aberto != null) {
                t.setValorAberto(new BigDecimal(aberto));
            }
            t.setValor(new BigDecimal("9999"));
            return t;
        }

        @Test
        void separaSaldoAbertoPorVencimentoIncluindoDiaAtualSemUsarValorContabilizado() {
            var repo = mock(FinancialTransactionRepository.class);
            var ctx = mock(ClienteContextService.class);
            when(ctx.getClienteAtualId()).thenReturn(7L);
            var parcial = item("DESPESA", hoje.minusDays(1), "20");
            parcial.setValorTotalAberto(new BigDecimal("22"));
            when(repo.findByClienteId(7L)).thenReturn(List.of(item("RECEITA", hoje.minusDays(1), "10"),
                    item("RECEITA", hoje, "30"), item("RECEITA", hoje.plusDays(1), "40"), parcial,
                    item("DESPESA", hoje, "5"), item("DESPESA", hoje, "0")));
            var r = new FinancialPositionService(repo, ctx).consultar(hoje);
            assertEquals(7L, r.clienteId());
            assertEquals(hoje, r.referencia());
            assertEquals(0, new BigDecimal("10").compareTo(r.receber().vencidos()));
            assertEquals(0, new BigDecimal("70").compareTo(r.receber().aVencer()));
            assertEquals(0, new BigDecimal("22").compareTo(r.pagar().vencidos()));
            assertEquals(0, new BigDecimal("5").compareTo(r.pagar().aVencer()));
            verify(repo).findByClienteId(7L);
        }

        @Test
        void evidenciaDadosAusentesSemInventarSaldoNemVencimento() {
            var repo = mock(FinancialTransactionRepository.class);
            var ctx = mock(ClienteContextService.class);
            when(ctx.getClienteAtualId()).thenReturn(8L);
            var cancelado = item("DESPESA", hoje, "50");
            cancelado.setSituacao("Cancelado");
            when(repo.findByClienteId(8L)).thenReturn(List.of(item("RECEITA", hoje, null),
                    item("DESPESA", null, "10"), item("DESPESA", hoje, "-5"), cancelado));
            var r = new FinancialPositionService(repo, ctx).consultar(hoje);
            assertEquals(1, r.semValorAberto());
            assertEquals(1, r.semVencimento());
            assertEquals(0, r.pagar().aVencer().signum());
            assertEquals(0, r.receber().aVencer().signum());
        }

        @Test
        void agrupaAusenciasPorOrigemSemInferirQuitacaoDeRegistroHistorico() {
            var repo = mock(FinancialTransactionRepository.class);
            var ctx = mock(ClienteContextService.class);
            when(ctx.getClienteAtualId()).thenReturn(7L);
            var excel = item("DESPESA", hoje.minusDays(50), null);
            excel.setOrigem("EXCEL");
            excel.setData(hoje.minusYears(1));
            excel.setSituacao("Pago");
            var csv = item("RECEITA", hoje.minusDays(20), null);
            csv.setOrigem("CSV");
            csv.setData(hoje);
            var api = item("DESPESA", null, "15");
            api.setOrigem("CONTA_AZUL_API");
            api.setData(hoje);
            when(repo.findByClienteId(7L)).thenReturn(List.of(excel, csv, api));
            var r = new FinancialPositionService(repo, ctx).consultar(hoje);
            assertEquals(2, r.semValorAberto());
            assertEquals(1, r.semVencimento());
            assertEquals(3, r.cobertura().size());
            assertEquals(hoje.minusYears(1), r.cobertura().stream().filter(c -> c.origem().equals("EXCEL")).findFirst().orElseThrow().primeiraDataAnalise());
            assertEquals(0, r.pagar().vencidos().signum());
            assertEquals(0, r.receber().vencidos().signum());
            assertNull(excel.getValorAberto());
            verify(repo, never()).save(any());
        }

        @Test
        void campoDeOrigemAusenteNaoImpedeDiagnostico() {
            var repo = mock(FinancialTransactionRepository.class);
            var ctx = mock(ClienteContextService.class);
            when(ctx.getClienteAtualId()).thenReturn(9L);
            when(repo.findByClienteId(9L)).thenReturn(List.of(item("DESPESA", hoje, null)));
            var r = new FinancialPositionService(repo, ctx).consultar(hoje);
            assertEquals("NAO_INFORMADA", r.cobertura().get(0).origem());
            assertNull(r.cobertura().get(0).primeiraDataAnalise());
        }

    }
