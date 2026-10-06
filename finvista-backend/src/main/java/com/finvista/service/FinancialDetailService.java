package com.finvista.service;

import com.finvista.dto.FinancialDetailResponse;
import com.finvista.model.FinancialTransaction;
import com.finvista.repository.FinancialTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class FinancialDetailService {

    private final FinancialTransactionRepository repository;
    private final ClienteContextService contexto;
    private final FinancialReferenceService referencia;

    public FinancialDetailService(FinancialTransactionRepository repository,
            ClienteContextService contexto, FinancialReferenceService referencia) {
        this.repository = repository;
        this.contexto = contexto;
        this.referencia = referencia;
    }

    @Transactional(readOnly = true)
    public FinancialDetailResponse consultar(String tipo, YearMonth mes, int pagina, int tamanho) {
        String normalizado = tipo == null ? "" : tipo.trim().toUpperCase(Locale.ROOT);
        if (!"RECEITA".equals(normalizado) && !"DESPESA".equals(normalizado)) {
            throw new IllegalArgumentException("Informe RECEITA ou DESPESA para detalhar o cartão.");
        }
        if (pagina < 0 || tamanho < 1 || tamanho > 50) {
            throw new IllegalArgumentException("Página deve ser zero ou maior; tamanho deve estar entre 1 e 50.");
        }
        YearMonth periodo = mes == null ? referencia.obterMesReferencia() : mes;
        Long clienteId = contexto.getClienteAtualId();
        
        List<FinancialTransaction> lancamentos = repository
                .findByClienteIdAndDataBetweenOrderByDataAsc(clienteId, periodo.atDay(1), periodo.atEndOfMonth())
                .stream().filter(t -> t.getData() != null && periodo.equals(YearMonth.from(t.getData()))
                && t.getValor() != null && t.getTipo() != null
                && normalizado.equals(t.getTipo().trim().toUpperCase(Locale.ROOT)))
                .sorted(Comparator.comparing(FinancialTransaction::getData)
                        .thenComparing(FinancialTransaction::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        BigDecimal total = lancamentos.stream().map(FinancialTransaction::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int quantidade = lancamentos.size();
        int totalPaginas = (int) ((quantidade + (long) tamanho - 1) / tamanho);
        long deslocamento = (long) pagina * tamanho;
        List<FinancialDetailResponse.Item> itens = lancamentos.stream().skip(deslocamento).limit(tamanho)
                .map(t -> new FinancialDetailResponse.Item(t.getId(), t.getData(), t.getDataVencimento(),
                t.getDataRealizacao(), t.getDescricao(), t.getSituacao(), t.getValor(),
                t.getValorOriginal(), t.getValorRealizado(), t.getValorAberto(), t.getOrigem()))
                .toList();
        String explicacao = "Soma do valor contabilizado dos lançamentos de " + normalizado
                + " pela data de análise entre " + periodo.atDay(1) + " e " + periodo.atEndOfMonth()
                + ". Cada lançamento entra uma vez, mesmo quando tem vários rateios."
                + " O cálculo não exige quitação e não usa o valor pago como substituto."
                + " O total considera todas as páginas; esta página mostra apenas parte da lista.";
        return new FinancialDetailResponse(clienteId, normalizado, periodo.toString(),
                periodo.atDay(1), periodo.atEndOfMonth(), total, quantidade,
                pagina, tamanho, totalPaginas, explicacao, itens);
    }
}
