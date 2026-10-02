package com.finvista.controller;

import com.finvista.dto.FinancialReferenceResponse;
import com.finvista.service.FinancialReferenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/referencia-financeira")
public class FinancialReferenceController {

    private final FinancialReferenceService
            financialReferenceService;

    public FinancialReferenceController(
            FinancialReferenceService
                    financialReferenceService
    ) {
        this.financialReferenceService =
                financialReferenceService;
    }

    @GetMapping
    public FinancialReferenceResponse obterReferencia() {

        return financialReferenceService
                .obterReferencia();
    }
}