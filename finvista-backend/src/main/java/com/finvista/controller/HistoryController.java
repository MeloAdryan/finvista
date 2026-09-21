package com.finvista.controller;

import com.finvista.dto.HistoryResponse;
import com.finvista.service.HistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/historico")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(
            HistoryService historyService
    ) {
        this.historyService =
                historyService;
    }

    @GetMapping
    public List<HistoryResponse> getHistorico(
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim
    ) {
        return historyService.listar(
                inicio,
                fim
        );
    }
}