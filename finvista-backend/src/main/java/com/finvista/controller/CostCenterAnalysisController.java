package com.finvista.controller;

import com.finvista.dto.*;
import com.finvista.model.CostBehavior;
import com.finvista.model.CostNature;
import com.finvista.service.CostCenterAnalysisService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/centros-custo")
public class CostCenterAnalysisController {

    private final CostCenterAnalysisService service;

    public CostCenterAnalysisController(CostCenterAnalysisService service) {
        this.service = service;
    }

    @GetMapping("/analise")
    public CostCenterAnalysisResponse analisar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) CostBehavior comportamento,
            @RequestParam(required = false) CostNature natureza) {
        return service.analisar(inicio, fim, comportamento, natureza);
    }

    @GetMapping("/classificacoes")
    public List<CostClassificationDto> listar() {
        return service.listarClassificacoes();
    }

    @PutMapping("/classificacoes")
    public CostClassificationDto salvar(@RequestBody CostClassificationDto dto) {
        return service.salvar(dto);
    }
}
