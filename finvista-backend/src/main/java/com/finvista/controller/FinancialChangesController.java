package com.finvista.controller;

import com.finvista.dto.FinancialChangesResponse;
import com.finvista.service.FinancialChangesService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class FinancialChangesController {
    private final FinancialChangesService service;

    public FinancialChangesController(FinancialChangesService service) {
        this.service = service;
    }

    @GetMapping("/mudancas")
    public FinancialChangesResponse obterMudancas() {
        return service.obterMudancas();
    }
}