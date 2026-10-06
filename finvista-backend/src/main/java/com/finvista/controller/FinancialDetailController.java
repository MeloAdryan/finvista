package com.finvista.controller;

import com.finvista.dto.FinancialDetailResponse;
import com.finvista.service.FinancialDetailService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.DateTimeException;
import java.time.YearMonth;

@RestController
@RequestMapping("/api/detalhamento-financeiro")
public class FinancialDetailController {

    private final FinancialDetailService service;

    public FinancialDetailController(FinancialDetailService service) {
        this.service = service;
    }

    @GetMapping
    public FinancialDetailResponse consultar(
            @RequestParam("tipo") String tipo,
            @RequestParam(value = "mes", required = false) String mes,
            @RequestParam(value = "pagina", defaultValue = "0") int pagina,
            @RequestParam(value = "tamanho", defaultValue = "20") int tamanho) {
        try {
            YearMonth periodo = mes == null ? null : YearMonth.parse(mes);
            return service.consultar(tipo, periodo, pagina, tamanho);
        } catch (IllegalArgumentException | DateTimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Confira tipo (RECEITA/DESPESA), mês (AAAA-MM), página e tamanho da página.");
        }
    }
}


    