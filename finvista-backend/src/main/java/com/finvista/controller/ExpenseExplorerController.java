package com.finvista.controller;

import com.finvista.dto.ExpenseExplorerResponse;
import com.finvista.model.CostBehavior;
import com.finvista.model.CostNature;
import com.finvista.service.ExpenseExplorerService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/despesas/analise")
public class ExpenseExplorerController {

    private final ExpenseExplorerService service;

    public ExpenseExplorerController(ExpenseExplorerService service) {
        this.service = service;
    }

    @GetMapping
    public ExpenseExplorerResponse consultar(
            @RequestParam(name = "inicio", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(name = "fim", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(name = "centroCusto", required = false) String centro,
            @RequestParam(name = "categoria", required = false) String categoria,
            @RequestParam(name = "comportamento", required = false) CostBehavior comportamento,
            @RequestParam(name = "natureza", required = false) CostNature natureza) {
        try {
            return service.consultar(inicio, fim, centro, categoria, comportamento, natureza);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
