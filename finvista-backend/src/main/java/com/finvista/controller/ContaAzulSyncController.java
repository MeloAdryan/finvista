package com.finvista.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvista.dto.importacao.ContaAzulSyncResponse.Candidato;
import com.finvista.dto.importacao.ContaAzulSyncResponse.Decisao;
import com.finvista.dto.importacao.ContaAzulSyncResponse.Plano;
import com.finvista.dto.importacao.ContaAzulSyncResponse.Resultado;
import com.finvista.service.ContaAzulSyncService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/importacoes/conta-azul/sincronizacao")
public class ContaAzulSyncController {

    private static final ZoneId FUSO_HORARIO =
            ZoneId.of("America/Sao_Paulo");

    private final ContaAzulSyncService service;
    private final ObjectMapper json;

    public ContaAzulSyncController(
            ContaAzulSyncService service,
            ObjectMapper json
    ) {
        this.service = service;
        this.json = json;
    }

    @PostMapping("/planejar")
    public Plano planejar(
            @RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam(value = "dataCorte", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dataCorte
    ) {
        try {
            return service.planejar(
                    arquivo,
                    resolverDataCorte(dataCorte)
            );
        } catch (IllegalArgumentException | IOException e) {
            throw erro(e);
        }
    }

    @PostMapping("/aplicar")
    public Resultado aplicar(
            @RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam("token") String token,
            @RequestParam("decisoes") String decisoes,
            @RequestParam("dataCorte")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dataCorte
    ) {
        try {
            List<Decisao> escolhas = json.readValue(
                    decisoes,
                    new TypeReference<List<Decisao>>() {}
            );

            return service.aplicar(
                    arquivo,
                    dataCorte,
                    token,
                    escolhas
            );
        } catch (IllegalArgumentException | IOException e) {
            throw erro(e);
        }
    }

    @GetMapping("/candidatos/{id}")
    public Candidato consultarCandidato(
            @PathVariable("id") Long id
    ) {
        return service.consultarCandidato(id);
    }

    private LocalDate resolverDataCorte(LocalDate dataCorte) {
        return dataCorte == null
                ? LocalDate.now(FUSO_HORARIO)
                : dataCorte;
    }

    private ResponseStatusException erro(Exception e) {
        String mensagem = e instanceof IOException
                ? "Falha ao ler arquivo ou decisões JSON."
                : e.getMessage();

        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                mensagem
        );
    }
}