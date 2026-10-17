package com.finvista.service;

import com.finvista.dto.SpendingGoalRecurrenceRequest;
import com.finvista.dto.SpendingGoalRecurrencePreview;
import com.finvista.dto.SpendingGoalRecurrencePreview.Periodo;
import com.finvista.model.SpendingGoal;
import com.finvista.repository.SpendingGoalRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class SpendingGoalRecurrenceService {

    private final SpendingGoalRepository repository;
    private final ClienteContextService context;

    public SpendingGoalRecurrenceService(SpendingGoalRepository repository, ClienteContextService context) {
        this.repository = repository;
        this.context = context;
    }

    public SpendingGoalRecurrencePreview previa(SpendingGoalRecurrenceRequest pedido) {
        administrador();
        if (pedido == null || pedido.dataInicio() == null || pedido.dataFim() == null) {
            throw new IllegalArgumentException("Informe o início e o fim da vigência.");
        }
        LocalDate inicio = pedido.dataInicio(), fim = pedido.dataFim();
        if (inicio.getYear() < 1900 || fim.getYear() > 9998 || fim.isBefore(inicio)
                || fim.isAfter(inicio.plusYears(10).minusDays(1))) {
            throw new IllegalArgumentException("Informe uma vigência válida de até dez anos.");
        }
        if (pedido.nome() == null || pedido.nome().isBlank() || pedido.nome().trim().length() > 120) {
            throw new IllegalArgumentException("Informe um nome de até 120 caracteres.");
        }
        BigDecimal valor = pedido.valorLimite();
        if (valor == null || valor.signum() <= 0 || valor.compareTo(new BigDecimal("9999999999999.99")) > 0
                || valor.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Informe um limite positivo com até duas casas decimais.");
        }
        if (pedido.percentualAlerta() == null || pedido.percentualAlerta() < 1 || pedido.percentualAlerta() > 100) {
            throw new IllegalArgumentException("O percentual de alerta deve estar entre 1 e 100.");
        }
        if ((pedido.categoria() != null && pedido.categoria().length() > 255)
                || (pedido.centroCusto() != null && pedido.centroCusto().length() > 255)) {
            throw new IllegalArgumentException("Categoria e centro de custo devem ter até 255 caracteres.");
        }
        String tipo = pedido.recorrencia() == null ? "" : pedido.recorrencia().trim().toUpperCase(Locale.ROOT);
        int meses = switch (tipo) {
            case "SEM_RECORRENCIA" ->
                0;
            case "MENSAL" ->
                1;
            case "TRIMESTRAL" ->
                3;
            case "SEMESTRAL" ->
                6;
            case "ANUAL" ->
                12;
            default ->
                throw new IllegalArgumentException("Selecione uma recorrência válida.");
        };
        List<Periodo> periodos = new ArrayList<>();
        LocalDate cursor = inicio;
        while (!cursor.isAfter(fim)) {
            LocalDate base = meses == 0 ? inicio : LocalDate.of(cursor.getYear(),
                    ((cursor.getMonthValue() - 1) / meses) * meses + 1, 1);
            LocalDate fimCompleto = meses == 0 ? fim : base.plusMonths(meses).minusDays(1);
            LocalDate termino = fim.isBefore(fimCompleto) ? fim : fimCompleto;
            long dias = ChronoUnit.DAYS.between(cursor, termino) + 1;
            long completos = ChronoUnit.DAYS.between(base, fimCompleto) + 1;
            BigDecimal limite = valor.multiply(BigDecimal.valueOf(dias))
                    .divide(BigDecimal.valueOf(completos), 2, RoundingMode.HALF_UP);
            if (limite.signum() <= 0) {
                throw new IllegalArgumentException("O limite proporcional de um período ficou abaixo de R$ 0,01. Aumente o limite.");
            }
            periodos.add(new Periodo(cursor, termino, limite, dias, completos, dias != completos));
            if (periodos.size() > 120) {
                throw new IllegalArgumentException("A vigência pode conter no máximo 120 períodos.");
            }
            cursor = termino.plusDays(1);
        }
        BigDecimal total = periodos.stream().map(Periodo::limite).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SpendingGoalRecurrencePreview(tipo, total, List.copyOf(periodos));
    }

    @Transactional
    public Criacao criar(SpendingGoalRecurrenceRequest pedido) {
        var previa = previa(pedido);
        var cliente = context.getClienteAtual();
        String serieId = UUID.randomUUID().toString();
        List<SpendingGoal> metas = previa.periodos().stream().map(periodo -> {
            var meta = new SpendingGoal(previa.recorrencia(), periodo.inicio(), periodo.fim(),
                    periodo.limite(), pedido.percentualAlerta());
            meta.setCliente(cliente);
            meta.setCategoria(normalizar(pedido.categoria()));
            meta.setCentroCusto(normalizar(pedido.centroCusto()));
            meta.setSerieId(serieId);
            meta.setNome(pedido.nome().trim());
            meta.setVigenciaInicio(pedido.dataInicio());
            meta.setVigenciaFim(pedido.dataFim());
            meta.setLimitePeriodo(pedido.valorLimite().setScale(2));
            return meta;
        }).toList();
        var salvas = repository.saveAll(metas);
        repository.flush();
        return new Criacao(serieId, salvas.stream().map(SpendingGoal::getId).toList());
    }

    @Transactional
    public void excluirSerie(String serieId) {
        administrador();
        try {
            UUID.fromString(serieId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recorrência não encontrada.");
        }
        var metas = repository.findBySerieIdAndClienteIdOrderByDataInicioAsc(serieId, context.getClienteAtualId());
        if (metas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recorrência não encontrada para o cliente selecionado.");
        }
        repository.deleteAll(metas);
        repository.flush();
    }

    private void administrador() {
        if (!context.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas administradores podem gerenciar recorrências.");
        }
    }

    private String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    public record Criacao(String serieId, List<Long> periodosIds) {}
}
