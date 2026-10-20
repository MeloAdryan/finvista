package com.finvista.controller;

import com.finvista.dto.FinancialPositionResponse;
import com.finvista.service.FinancialPositionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard/posicao-financeira")
public class FinancialPositionController {
    private final FinancialPositionService service;
    public FinancialPositionController(FinancialPositionService service) { this.service = service; }
    @GetMapping
    public FinancialPositionResponse consultar() { return service.consultar(); }
}
