package com.finvista.controller;

import com.finvista.dto.importacao.ContaAzulConferenceResponse;
import com.finvista.service.ClienteContextService;
import com.finvista.service.ContaAzulConferenceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/importacoes/conta-azul")
public class ContaAzulConferenceController {

    private final ContaAzulConferenceService service;
    private final ClienteContextService contexto;

    public ContaAzulConferenceController(ContaAzulConferenceService service, ClienteContextService contexto) {
        this.service = service;
        this.contexto = contexto;
    }

    @PostMapping("/conferencia")
    public ContaAzulConferenceResponse conferir(@RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam(value = "dataCorte", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataCorte) {
        contexto.getClienteAtual();
        try {
            return service.conferir(arquivo, dataCorte == null
                    ? LocalDate.now(ZoneId.of("America/Sao_Paulo")) : dataCorte);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível ler o arquivo Excel.");
        }
    }
}
