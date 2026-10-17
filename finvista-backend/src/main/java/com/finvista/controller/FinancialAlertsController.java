package com.finvista.controller;

import com.finvista.dto.FinancialAlertsResponse;
import com.finvista.service.FinancialAlertsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/alertas-financeiros")
public class FinancialAlertsController {
    private final FinancialAlertsService service;

    public FinancialAlertsController(FinancialAlertsService service) {
        this.service = service;
    }

    @GetMapping
    public FinancialAlertsResponse consultar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String categoria) {
        return service.consultar(inicio, fim, categoria);
    }
}
