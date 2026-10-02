package com.finvista.controller;

import com.finvista.dto.SpendingGoalResponse;
import com.finvista.model.SpendingGoal;
import com.finvista.service.SpendingGoalService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/metas-gastos")
public class SpendingGoalController {

    private final SpendingGoalService spendingGoalService;

    public SpendingGoalController(
            SpendingGoalService spendingGoalService
    ) {
        this.spendingGoalService =
                spendingGoalService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SpendingGoal criar(
            @RequestBody SpendingGoal meta
    ) {
        return spendingGoalService.salvar(meta);
    }

    @GetMapping
    public List<SpendingGoalResponse> listar(
            @RequestParam(
                    required = false
            ) String centroCusto,

            @RequestParam(
                    required = false
            ) String categoria
    ) {
        return spendingGoalService.listarComSituacao(
                centroCusto,
                categoria
        );
    }

    @GetMapping("/{id}")
    public SpendingGoalResponse buscarPorId(
            @PathVariable Long id,
            @RequestParam(
                    required = false
            ) String centroCusto,
            @RequestParam(
                    required = false
            ) String categoria
    ) {
        return spendingGoalService.buscarSituacao(
                id,
                centroCusto,
                categoria
        );
    }
}