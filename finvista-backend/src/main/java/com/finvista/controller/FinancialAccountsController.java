package com.finvista.controller;

import com.finvista.dto.FinancialAccountsResponse;
import com.finvista.service.FinancialAccountsService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard/contas-financeiras")
public class FinancialAccountsController {

    private final FinancialAccountsService service;

    public FinancialAccountsController(FinancialAccountsService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<FinancialAccountsResponse> consultar() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.consultar());
    }
}