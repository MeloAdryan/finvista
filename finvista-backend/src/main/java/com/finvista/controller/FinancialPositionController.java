package com.finvista.controller;

import com.finvista.dto.FinancialPositionResponse;
import com.finvista.service.FinancialPositionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard/posicao-financeira")
public class FinancialPositionController {

    private final FinancialPositionService service;

    public FinancialPositionController(FinancialPositionService service) {
        this.service = service;
    }

    @GetMapping
    public org.springframework.http.ResponseEntity<FinancialPositionResponse> consultar(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate inicio,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate fim) {
        var result = inicio == null && fim == null ? service.consultar() : service.consultar(inicio, fim);
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(result);
    }
}
