package com.finvista.integration.contaazul;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

public final class ContaAzulSettlementPolicy {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    public record Valores(BigDecimal original, BigDecimal principalPago, BigDecimal principalAberto,
            BigDecimal totalPago, BigDecimal totalAberto, BigDecimal jurosPago, BigDecimal multaPaga,
            BigDecimal descontoPago, BigDecimal jurosAberto, BigDecimal multaAberta,
            BigDecimal descontoAberto, LocalDate ultimaBaixa, BigDecimal liquido,
            BigDecimal taxaPaga, BigDecimal taxaAberta, BigDecimal perda, BigDecimal renegociado) {}

    public static Valores conferir(ContaAzulParcelDetail p) {
        if (p == null) throw erro("Parcela ausente.");
        boolean perda = "PERDIDO".equals(p.status());
        boolean renegociado = "RENEGOCIADO".equals(p.status());
        boolean cancelado = Set.of("CANCELADO", "CANCELADA").contains(p.status() == null ? "" : p.status());
        boolean encerrado = perda || renegociado || cancelado;
        if ((p.perda() != null) != perda || (p.renegociacao() != null) != renegociado)
            throw erro("Situação e perda/renegociação não correspondem; exige conferência.");
        BigDecimal baixadoSemCaixa = ZERO;
        if (perda || renegociado) {
            var ajuste = perda ? p.perda() : p.renegociacao();
            valor(ajuste.valor());
            if (ajuste.valor().signum() <= 0) throw erro("Perda/renegociação exige valor positivo explícito.");
            if (perda && (ajuste.data() == null || ajuste.data().getYear() < 1900 || ajuste.data().getYear() > 2200))
                throw erro("Perda exige data explícita válida.");
            if (renegociado && (ajuste.id() == null || !ajuste.id().matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
                throw erro("Renegociação exige identidade externa explícita válida.");
            baixadoSemCaixa = ajuste.valor();
        }
        var c = p.composicao();
        BigDecimal liquido = liquidoComposicao(c);
        if (p.liquido() != null) {
            igual(p.liquido(), liquido, "Líquido principal e composição divergem.");
        }
        valor(p.pago());
        valor(p.pendente());
        if (cancelado && p.pago().add(p.pendente()).compareTo(liquido) == 0) {
            String estadoFinanceiro = p.pendente().signum() == 0 ? "QUITADO" : p.pago().signum() == 0 ? "PENDENTE" : "RECEBIDO_PARCIAL";
            var normal = new ContaAzulParcelDetail(p.id(), p.descricao(), estadoFinanceiro, p.vencimento(), p.liquido(), p.pago(), p.pendente(),
                    p.composicao(), p.evento(), null, null, p.baixas());
            var f = conferir(normal);
            return new Valores(f.original(), f.principalPago(), ZERO, f.totalPago(), ZERO, f.jurosPago(), f.multaPaga(), f.descontoPago(),
                    ZERO, ZERO, ZERO, f.ultimaBaixa(), f.liquido(), f.taxaPaga(), ZERO, ZERO, ZERO);
        }
        if (!encerrado) igual(p.pago().add(p.pendente()), liquido, "Pago mais pendente difere da composição.");
        String status = p.status() == null ? "" : p.status();
        if (!Set.of("PENDENTE", "EM_ABERTO", "ATRASADO", "RECEBIDO_PARCIAL", "QUITADO", "RECEBIDO", "PERDIDO", "RENEGOCIADO", "CANCELADO", "CANCELADA").contains(status)) {
            throw erro("Situação da parcela exige conferência específica.");
        }
        boolean quitado = Set.of("QUITADO", "RECEBIDO").contains(status);
        if (!encerrado && (quitado != (p.pendente().signum() == 0)
                || ("RECEBIDO_PARCIAL".equals(status) && (p.pago().signum() == 0 || p.pendente().signum() == 0)))) {
            throw erro("Situação e saldos da parcela divergem.");
        }
        BigDecimal principal = ZERO, total = ZERO, juros = ZERO, multa = ZERO, desconto = ZERO, taxa = ZERO;
        LocalDate ultima = null;
        Set<String> ids = new HashSet<>();
        if (p.baixas() != null) {
            if (p.baixas().size() > 1000) {
                throw erro("Quantidade de baixas excede o limite de conferência.");
            }
            for (var baixa : p.baixas()) {
                if (baixa == null || baixa.id() == null || baixa.id().isBlank()
                        || !ids.add(baixa.id().toLowerCase(java.util.Locale.ROOT)) || baixa.data() == null
                        || (baixa.parcelaId() != null && !p.id().equalsIgnoreCase(baixa.parcelaId()))) {
                    throw erro("Baixa duplicada, sem identidade/data ou de outra parcela.");
                }
                BigDecimal pagoLiquido = liquidoComposicao(baixa.composicao());
                principal = principal.add(baixa.composicao().bruto());
                total = total.add(pagoLiquido);
                juros = juros.add(baixa.composicao().juros());
                multa = multa.add(baixa.composicao().multa());
                desconto = desconto.add(baixa.composicao().desconto());
                taxa = taxa.add(baixa.composicao().taxa());
                if (ultima == null || baixa.data().isAfter(ultima)) {
                    ultima = baixa.data();
                }
            }
        } else if (p.pago().signum() != 0) {
            throw erro("A parcela tem pagamento, mas as baixas não foram informadas.");
        }
        valor(total); valor(juros); valor(multa); valor(desconto); valor(taxa);
        if (encerrado) {
            if (c.juros().signum() != 0 || c.multa().signum() != 0 || c.desconto().signum() != 0 || c.taxa().signum() != 0)
                throw erro("Perda/renegociação com ajustes nominais exige composição específica.");
            if (!cancelado || p.pago().compareTo(total) != 0)
                igual(p.pago(), principal, "Principal pago da parcela encerrada difere das baixas.");
            if (cancelado) {
                BigDecimal residual = diferenca(c.bruto(), principal);
                if (p.pendente().signum() != 0 && p.pendente().compareTo(residual) != 0)
                    throw erro("Saldo informado no cancelamento não corresponde ao principal remanescente.");
            } else {
                igual(p.pendente(), ZERO, "Parcela encerrada não pode manter saldo aberto.");
                igual(principal.add(baixadoSemCaixa), c.bruto(), "Principal pago mais perda/renegociação não fecha com o original.");
            }
            return new Valores(moeda(c.bruto()), moeda(principal), ZERO, moeda(total), ZERO,
                    moeda(juros), moeda(multa), moeda(desconto), ZERO, ZERO, ZERO, ultima, moeda(liquido),
                    moeda(taxa), ZERO, perda ? moeda(baixadoSemCaixa) : ZERO, renegociado ? moeda(baixadoSemCaixa) : ZERO);
        }
        // Some parcel responses report settled principal, while the dedicated payment
        // endpoint carries the actual interest/fine/discount. Accept this representation
        // only when the nominal parcel has explicitly zero adjustments and principal closes.
        boolean pagoRepresentaPrincipal = c.juros().signum() == 0 && c.multa().signum() == 0
                && c.desconto().signum() == 0 && c.taxa().signum() == 0 && principal.compareTo(p.pago()) == 0;
        if (pagoRepresentaPrincipal) {
            valor(total);
            valor(juros);
            valor(multa);
            valor(desconto);
            BigDecimal aberto = diferenca(c.bruto(), principal);
            igual(aberto, p.pendente(), "Principal aberto difere do saldo informado.");
            return new Valores(moeda(c.bruto()), moeda(principal), moeda(aberto), moeda(total), moeda(aberto),
                    moeda(juros), moeda(multa), moeda(desconto), ZERO, ZERO, ZERO, ultima, moeda(liquido), moeda(taxa), ZERO, ZERO, ZERO);
        }
        igual(total, p.pago(), "A soma líquida das baixas difere do pago informado.");
        BigDecimal aberto = diferenca(c.bruto(), principal);
        BigDecimal jurosAberto = diferenca(c.juros(), juros);
        BigDecimal multaAberta = diferenca(c.multa(), multa);
        BigDecimal descontoAberto = diferenca(c.desconto(), desconto);
        BigDecimal taxaAberta = diferenca(c.taxa(), taxa);
        igual(aberto.add(jurosAberto).add(multaAberta).subtract(descontoAberto).subtract(taxaAberta), p.pendente(),
                "O saldo principal e os ajustes não fecham com o pendente.");
        if (quitado && (aberto.signum() != 0 || jurosAberto.signum() != 0
                || multaAberta.signum() != 0 || descontoAberto.signum() != 0 || taxaAberta.signum() != 0)) {
            throw erro("Quitação exige saldo principal e ajustes remanescentes zerados.");
        }
        return new Valores(moeda(c.bruto()), moeda(principal), moeda(aberto), moeda(total), moeda(p.pendente()),
                moeda(juros), moeda(multa), moeda(desconto), moeda(jurosAberto), moeda(multaAberta),
                moeda(descontoAberto), ultima, moeda(liquido), moeda(taxa), moeda(taxaAberta), ZERO, ZERO);
    }

    public static BigDecimal liquidoComposicao(ContaAzulParcelDetail.Composicao c) {
        if (c == null) {
            throw erro("Composição financeira ausente.");
        }
        valor(c.bruto());
        valor(c.juros());
        valor(c.multa());
        valor(c.desconto());
        valor(c.taxa());
        if (c.taxa().signum() != 0 && c.liquido() == null) {
            throw erro("Taxa informada sem líquido explícito: não será presumida sua incidência.");
        }
        BigDecimal liquido = c.bruto().add(c.juros()).add(c.multa()).subtract(c.desconto()).subtract(c.taxa());
        valor(liquido);
        if (c.liquido() != null) {
            igual(c.liquido(), liquido, "Componentes e líquido da composição divergem.");
        }
        return liquido;
    }

    private static BigDecimal diferenca(BigDecimal a, BigDecimal b) {
        BigDecimal v = a.subtract(b);
        valor(v);
        return v;
    }

    private static void igual(BigDecimal a, BigDecimal b, String mensagem) {
        valor(a);
        valor(b);
        if (a.compareTo(b) != 0) {
            throw erro(mensagem);
        }
    }

    private static void valor(BigDecimal v) {
        if (v == null || v.signum() < 0 || v.stripTrailingZeros().scale() > 2
                || v.compareTo(new BigDecimal("9999999999999.99")) > 0) {
            throw erro("Componente ausente, negativo ou fora da precisão aceita.");
        }
    }

    private static BigDecimal moeda(BigDecimal v) {
        return v.setScale(2);
    }

    private static IllegalArgumentException erro(String s) {
        return new IllegalArgumentException(s);
    }
}
