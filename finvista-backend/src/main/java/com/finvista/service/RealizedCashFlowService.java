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
        boolean possuiBaixasFuturas = false;
        for (var t : transactions.findByClienteId(cliente)) {
            if (!Set.of("RECEITA", "DESPESA").contains(t.getTipo() == null ? "" : t.getTipo().trim().toUpperCase(Locale.ROOT))) {
                continue;
            }
            var o = atuais.get(t.getId());
            try {
                var audited = AuditedSettlementReader.read(t, o, ambiguos.contains(t.getId()), json, hoje);
                possuiBaixasFuturas |= audited.baixasFuturas() > 0;
                for (var baixa : audited.pagamentos()) {
                    movimentos.add(new Movimento(baixa.data(), baixa.liquido(), "RECEITA".equals(t.getTipo())));
                }
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
            } else if (!m.data().isAfter(fim)) {
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
        return new RealizedCashFlowResponse(cliente, inicio, fim, conhecidos, desconhecidos, baixas, desconhecidos > 0 || possuiBaixasFuturas,
                "BAIXAS_AUDITADAS_LIQUIDAS", List.copyOf(resultado));
    }
}
