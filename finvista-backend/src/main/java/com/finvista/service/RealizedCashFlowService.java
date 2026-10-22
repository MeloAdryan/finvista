package com.finvista.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvista.dto.*;
import com.finvista.model.*;
import com.finvista.repository.*;
import com.finvista.integration.contaazul.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RealizedCashFlowService {

    private final FinancialTransactionRepository transactions;
    private final ContaAzulApiOperationRepository operations;
    private final ClienteContextService context;
    private final ObjectMapper json;
    private final Clock clock;

    public RealizedCashFlowService(FinancialTransactionRepository transactions, ContaAzulApiOperationRepository operations,
            ClienteContextService context, ObjectMapper json, Clock clock) {
        this.transactions = transactions;
        this.operations = operations;
        this.context = context;
        this.json = json;
        this.clock = clock;
    }

    private record Movimento(LocalDate data, BigDecimal valor, boolean receita) {}

    @Transactional(readOnly = true)
    public RealizedCashFlowResponse consultar(String periodo) {
        int quantidade;
        boolean todos = "todos".equalsIgnoreCase(periodo);
        try {
            quantidade = todos ? 6 : Integer.parseInt(periodo == null ? "6" : periodo);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Período inválido.");
        }
        if (quantidade < 1 || quantidade > 120) {
            throw new IllegalArgumentException("Selecione de 1 a 120 meses ou todos.");
        }
        Long cliente = context.getClienteAtualId();
        LocalDate hoje = LocalDate.now(clock.withZone(ZoneId.of("America/Sao_Paulo")));
        Map<Long, ContaAzulApiOperation> atuais = new HashMap<>();
        Set<Long> ambiguos = new HashSet<>();
        for (var o : operations.snapshotsAtuais(cliente)) {
            var existente = atuais.putIfAbsent(o.getLancamentoId(), o);
            if (existente != null && !Objects.equals(existente.getOrigemSnapshot(), o.getOrigemSnapshot())) {
                ambiguos.add(o.getLancamentoId());
            }
        }
        List<Movimento> movimentos = new ArrayList<>();
        int conhecidos = 0, desconhecidos = 0;
        for (var t : transactions.findByClienteId(cliente)) {
            if (!Set.of("RECEITA", "DESPESA").contains(t.getTipo() == null ? "" : t.getTipo().trim().toUpperCase(Locale.ROOT))) {
                continue;
            }
            var o = atuais.get(t.getId());
            try {
                if (o == null || ambiguos.contains(t.getId()) || !"CONTA_AZUL_API".equals(t.getOrigem())) {
                    throw new IllegalArgumentException();
                }
                var p = json.readValue(o.getOrigemSnapshot(), ContaAzulParcelDetail.class);
                if (p.evento() == null || p.id() == null || !p.id().equalsIgnoreCase(t.getDocumentoReferencia())
                        || !p.evento().tipo().equals(t.getTipo()) || !Objects.equals(p.evento().competencia(), t.getDataCompetencia())
                        || !Objects.equals(p.status(), t.getSituacao())) {
                    throw new IllegalArgumentException();
                }
                var v = ContaAzulSettlementPolicy.conferir(p);
                if (t.getValor() == null || t.getValor().compareTo(v.original()) != 0
                        || t.getValorTotalRealizado() == null || t.getValorTotalRealizado().compareTo(v.totalPago()) != 0
                        || t.getValorRealizado() == null || t.getValorRealizado().compareTo(v.principalPago()) != 0
                        || t.getValorTotalAberto() == null || t.getValorTotalAberto().compareTo(v.totalAberto()) != 0) {
                    throw new IllegalArgumentException();
                }
                List<Movimento> parcela = new ArrayList<>();
                for (var baixa : p.baixas() == null ? List.<ContaAzulParcelDetail.Baixa>of() : p.baixas()) {
                    if (baixa.data().isAfter(hoje) || baixa.data().isBefore(LocalDate.of(2000, 1, 1))) {
                        throw new IllegalArgumentException();
                    }
                    var c = baixa.composicao();
                    parcela.add(new Movimento(baixa.data(), c.bruto().add(c.juros()).add(c.multa()).subtract(c.desconto()), "RECEITA".equals(t.getTipo())));
                }
                movimentos.addAll(parcela);
                conhecidos++;
            } catch (Exception e) {
                // Registro desconhecido não vira zero de caixa nem é alocado na competência.
                desconhecidos++;
            }
        }
        YearMonth atual = YearMonth.from(hoje);
        LocalDate inicio = atual.minusMonths(quantidade - 1L).atDay(1);
        if (todos) {
            inicio = movimentos.stream().map(Movimento::data).min(LocalDate::compareTo).map(d -> YearMonth.from(d).atDay(1)).orElse(atual.atDay(1));
        }
        LocalDate fim = atual.atEndOfMonth();
        Map<YearMonth, BigDecimal[]> meses = new LinkedHashMap<>();
        for (YearMonth m = YearMonth.from(inicio); !m.isAfter(atual); m = m.plusMonths(1)) {
            meses.put(m, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }
        BigDecimal saldo = BigDecimal.ZERO;
        int baixas = 0;
        for (var m : movimentos) {
            if (m.data().isBefore(inicio)) {
                saldo = saldo.add(m.receita() ? m.valor() : m.valor().negate());
            }else if (!m.data().isAfter(fim)) {
                var a = meses.get(YearMonth.from(m.data()));
                a[m.receita() ? 0 : 1] = a[m.receita() ? 0 : 1].add(m.valor());
                baixas++;
            }
        }
        List<CashFlowResponse> resultado = new ArrayList<>();
        for (var e : meses.entrySet()) {
            var a = e.getValue();
            BigDecimal proximo = saldo.add(a[0]).subtract(a[1]);
            resultado.add(new CashFlowResponse(e.getKey().toString(), saldo, a[0], a[1], proximo));
            saldo = proximo;
        }
        if (!cliente.equals(context.getClienteAtualId())) {
            throw new IllegalStateException("A empresa selecionada mudou.");
        }
        return new RealizedCashFlowResponse(cliente, inicio, fim, conhecidos, desconhecidos, baixas, desconhecidos > 0,
                "BAIXAS_AUDITADAS_LIQUIDAS", List.copyOf(resultado));
    }
}
