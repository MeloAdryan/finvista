package com.finvista.controller;

import com.finvista.dto.DashboardAnalysisResponse;
import com.finvista.service.DashboardAnalysisService;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/dashboard/analise")
public class DashboardAnalysisController {

    private final DashboardAnalysisService service;

    public DashboardAnalysisController(DashboardAnalysisService service) {
        this.service = service;
    }

    @GetMapping
    public DashboardAnalysisResponse consultar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String categoria) {
        try {
            return service.consultar(inicio, fim, categoria);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
