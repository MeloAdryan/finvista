package com.finvista.service;

import com.finvista.dto.FinancialPositionResponse;
import com.finvista.repository.FinancialTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Service
public class FinancialPositionService {

    private final FinancialTransactionRepository repository;
    private final ClienteContextService contexto;

    public FinancialPositionService(FinancialTransactionRepository repository, ClienteContextService contexto) {
        this.repository = repository;
        this.contexto = contexto;
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public FinancialPositionResponse consultar() {
        return consultar(LocalDate.now(ZoneId.of("America/Sao_Paulo")));
    }

    FinancialPositionResponse consultar(LocalDate hoje) {
        Long clienteId = contexto.getClienteAtualId();
        BigDecimal[] receber = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        BigDecimal[] pagar = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        int semValor = 0, semVencimento = 0;
        Map<String, Resumo> cobertura = new TreeMap<>();

        for (var item : repository.findByClienteId(clienteId)) {
            var rows = FinancialOpenItems.read(List.of(item), hoje);
            if (rows.isEmpty()) {
                continue;
            }
            var open = rows.get(0);
            String tipo = open.tipo();
            BigDecimal aberto = open.aberto();
            if (aberto == null) {
                semValor++;
                registrar(cobertura, item, true);
                continue;
            }
            if (open.vencimento() == null) {
                semVencimento++;
                registrar(cobertura, item, false);
                continue;
            }
            BigDecimal[] grupo = tipo.equals("RECEITA") ? receber : pagar;
            int indice = open.vencimento().isBefore(hoje) ? 0 : 1;
            grupo[indice] = grupo[indice].add(aberto);
            if (open.vencimento().equals(hoje)) {
                grupo[2] = grupo[2].add(aberto);
            }
        }
        if (!clienteId.equals(contexto.getClienteAtualId())) {
            throw new IllegalArgumentException("A empresa mudou durante a consulta.");
        }
        return new FinancialPositionResponse(clienteId, hoje,
                new FinancialPositionResponse.Grupo(receber[0], receber[1], receber[2], receber[1].subtract(receber[2])),
                new FinancialPositionResponse.Grupo(pagar[0], pagar[1], pagar[2], pagar[1].subtract(pagar[2])), semValor, semVencimento, cobertura.entrySet().stream().map(e -> new FinancialPositionResponse.Cobertura(
                        e.getKey(), e.getValue().semValor, e.getValue().semVencimento, e.getValue().primeira, e.getValue().ultima)).toList());
    }

    private static class Resumo {

        int semValor, semVencimento;
        LocalDate primeira, ultima;
    }

    private static void registrar(Map<String, Resumo> mapa, com.finvista.model.FinancialTransaction item, boolean semValor) {
        String origem = item.getOrigem() == null || item.getOrigem().isBlank() ? "NAO_INFORMADA" : item.getOrigem().trim().toUpperCase(Locale.ROOT);
        var r = mapa.computeIfAbsent(origem, k -> new Resumo());
        if (semValor) {
            r.semValor++;
        } else {
            r.semVencimento++;
        }
        LocalDate data = item.getData();
        if (data != null) {
            if (r.primeira == null || data.isBefore(r.primeira)) {
                r.primeira = data;
            }
            if (r.ultima == null || data.isAfter(r.ultima)) {
                r.ultima = data;
            }
        }
    }
}
