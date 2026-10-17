package com.finvista.controller;

import com.finvista.dto.SpendingGoalRecurrenceRequest;
import com.finvista.dto.SpendingGoalRecurrencePreview;
import com.finvista.service.SpendingGoalRecurrenceService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/metas-gastos/recorrencias")
public class SpendingGoalRecurrenceController {

    private final SpendingGoalRecurrenceService service;

    public SpendingGoalRecurrenceController(SpendingGoalRecurrenceService service) {
        this.service = service;
    }

    @PostMapping("/previa")
    public SpendingGoalRecurrencePreview previa(@RequestBody SpendingGoalRecurrenceRequest pedido) {
        return service.previa(pedido);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SpendingGoalRecurrenceService.Criacao criar(@RequestBody SpendingGoalRecurrenceRequest pedido) {
        return service.criar(pedido);
    }

    @DeleteMapping("/{serieId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable String serieId) {
        service.excluirSerie(serieId);
    }
}
