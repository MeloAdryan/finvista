package com.finvista.integration.contaazul;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

final class ContaAzulSettlementPolicy {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    record Valores(BigDecimal original, BigDecimal principalPago, BigDecimal principalAberto,
            BigDecimal totalPago, BigDecimal totalAberto, BigDecimal jurosPago, BigDecimal multaPaga,
            BigDecimal descontoPago, BigDecimal jurosAberto, BigDecimal multaAberta,
            BigDecimal descontoAberto, LocalDate ultimaBaixa, BigDecimal liquido) {}

    static Valores conferir(ContaAzulParcelDetail p) {
        if (p == null || p.perda() != null || p.renegociacao() != null)
            throw erro("Perdas e renegociações exigem conferência específica.");
        var c = p.composicao();
        BigDecimal liquido = composicao(c);
        if (p.liquido() != null) igual(p.liquido(), liquido, "Líquido principal e composição divergem.");
        valor(p.pago()); valor(p.pendente());
        igual(p.pago().add(p.pendente()), liquido, "Pago mais pendente difere da composição.");
        String status = p.status() == null ? "" : p.status();
        if (!Set.of("PENDENTE", "EM_ABERTO", "ATRASADO", "RECEBIDO_PARCIAL", "QUITADO", "RECEBIDO").contains(status))
            throw erro("Situação da parcela exige conferência específica.");
        boolean quitado = Set.of("QUITADO", "RECEBIDO").contains(status);
        if (quitado != (p.pendente().signum() == 0)
                || ("RECEBIDO_PARCIAL".equals(status) && (p.pago().signum() == 0 || p.pendente().signum() == 0)))
            throw erro("Situação e saldos da parcela divergem.");
        BigDecimal principal = ZERO, total = ZERO, juros = ZERO, multa = ZERO, desconto = ZERO;
        LocalDate ultima = null;
        Set<String> ids = new HashSet<>();
        if (p.baixas() != null) {
            if (p.baixas().size() > 1000) throw erro("Quantidade de baixas excede o limite de conferência.");
            for (var baixa : p.baixas()) {
                if (baixa == null || baixa.id() == null || baixa.id().isBlank()
                        || !ids.add(baixa.id().toLowerCase(java.util.Locale.ROOT)) || baixa.data() == null
                        || (baixa.parcelaId() != null && !p.id().equalsIgnoreCase(baixa.parcelaId())))
                    throw erro("Baixa duplicada, sem identidade/data ou de outra parcela.");
                BigDecimal pagoLiquido = composicao(baixa.composicao());
                principal = principal.add(baixa.composicao().bruto());
                total = total.add(pagoLiquido);
                juros = juros.add(baixa.composicao().juros());
                multa = multa.add(baixa.composicao().multa());
                desconto = desconto.add(baixa.composicao().desconto());
                if (ultima == null || baixa.data().isAfter(ultima)) ultima = baixa.data();
            }
        } else if (p.pago().signum() != 0) {
            throw erro("A parcela tem pagamento, mas as baixas não foram informadas.");
        }
        igual(total, p.pago(), "A soma líquida das baixas difere do pago informado.");
        BigDecimal aberto = diferenca(c.bruto(), principal);
        BigDecimal jurosAberto = diferenca(c.juros(), juros);
        BigDecimal multaAberta = diferenca(c.multa(), multa);
        BigDecimal descontoAberto = diferenca(c.desconto(), desconto);
        igual(aberto.add(jurosAberto).add(multaAberta).subtract(descontoAberto), p.pendente(),
                "O saldo principal e os ajustes não fecham com o pendente.");
        if (quitado && (aberto.signum() != 0 || jurosAberto.signum() != 0
                || multaAberta.signum() != 0 || descontoAberto.signum() != 0))
            throw erro("Quitação exige saldo principal e ajustes remanescentes zerados.");
        return new Valores(moeda(c.bruto()), moeda(principal), moeda(aberto), moeda(total), moeda(p.pendente()),
                moeda(juros), moeda(multa), moeda(desconto), moeda(jurosAberto), moeda(multaAberta),
                moeda(descontoAberto), ultima, moeda(liquido));
    }

    private static BigDecimal composicao(ContaAzulParcelDetail.Composicao c) {
        if (c == null) throw erro("Composição financeira ausente.");
        valor(c.bruto()); valor(c.juros()); valor(c.multa()); valor(c.desconto()); valor(c.taxa());
        if (c.taxa().signum() != 0) throw erro("Taxas não zeradas exigem conferência específica; não serão convertidas em desconto.");
        BigDecimal liquido = c.bruto().add(c.juros()).add(c.multa()).subtract(c.desconto());
        valor(liquido);
        if (c.liquido() != null) igual(c.liquido(), liquido, "Componentes e líquido da composição divergem.");
        return liquido;
    }
    private static BigDecimal diferenca(BigDecimal a, BigDecimal b) {
        BigDecimal v = a.subtract(b); valor(v); return v;
    }
    private static void igual(BigDecimal a, BigDecimal b, String mensagem) {
        valor(a); valor(b); if (a.compareTo(b) != 0) throw erro(mensagem);
    }
    private static void valor(BigDecimal v) {
        if (v == null || v.signum() < 0 || v.stripTrailingZeros().scale() > 2
                || v.compareTo(new BigDecimal("9999999999999.99")) > 0)
            throw erro("Componente ausente, negativo ou fora da precisão aceita.");
    }
    private static BigDecimal moeda(BigDecimal v) { return v.setScale(2); }
    private static IllegalArgumentException erro(String s) { return new IllegalArgumentException(s); }
}
