package com.finvista.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.finvista.dto.importacao.ImportColumnMappingRequest;
import com.finvista.dto.importacao.ImportPreviewResponse;
import com.finvista.model.FinancialTransaction;
import com.finvista.repository.FinancialTransactionRepository;
import com.finvista.service.CsvImportService;
import com.finvista.service.ExcelImportService;
import com.finvista.service.FinancialImportService;
import com.finvista.service.ImportMappingService;
import com.finvista.service.ImportPreviewService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/importacoes")
public class ImportController {

    private final CsvImportService csvImportService;
    private final ExcelImportService excelImportService;
    private final FinancialImportService financialImportService;
    private final ImportPreviewService importPreviewService;
    private final ImportMappingService importMappingService;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final ObjectMapper objectMapper;

    public ImportController(
            CsvImportService csvImportService,
            ExcelImportService excelImportService,
            FinancialImportService financialImportService,
            ImportPreviewService importPreviewService,
            ImportMappingService importMappingService,
            FinancialTransactionRepository financialTransactionRepository,
            ObjectMapper objectMapper
    ) {
        this.csvImportService = csvImportService;
        this.excelImportService = excelImportService;
        this.financialImportService = financialImportService;
        this.importPreviewService = importPreviewService;
        this.importMappingService = importMappingService;
        this.financialTransactionRepository = financialTransactionRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/lancamentos")
    public ResponseEntity<Map<String, Object>> listarLancamentos() {

        List<FinancialTransaction> lancamentos =
                financialTransactionRepository
                        .findAllByOrderByDataDesc();

        Map<String, Object> resposta =
                new LinkedHashMap<>();

        resposta.put("sucesso", true);
        resposta.put(
                "quantidade",
                lancamentos.size()
        );
        resposta.put(
                "lancamentos",
                lancamentos
        );

        return ResponseEntity.ok(resposta);
    }

    @PostMapping("/preview-estrutura")
    public ResponseEntity<Map<String, Object>> visualizarEstrutura(
            @RequestParam("arquivo") MultipartFile arquivo
    ) {

        Map<String, Object> resposta =
                new LinkedHashMap<>();

        try {

            ImportPreviewResponse preview =
                    importPreviewService
                            .gerarPreview(arquivo);

            resposta.put("sucesso", true);
            resposta.put(
                    "arquivo",
                    preview.getArquivo()
            );
            resposta.put(
                    "tipoArquivo",
                    preview.getTipoArquivo()
            );
            resposta.put(
                    "colunas",
                    preview.getColunas()
            );
            resposta.put(
                    "linhas",
                    preview.getLinhas()
            );
            resposta.put(
                    "quantidadeLinhas",
                    preview.getQuantidadeLinhas()
            );

            return ResponseEntity.ok(resposta);

        } catch (IllegalArgumentException exception) {

            resposta.put("sucesso", false);
            resposta.put(
                    "erro",
                    exception.getMessage()
            );

            return ResponseEntity
                    .badRequest()
                    .body(resposta);

        } catch (IOException exception) {

            resposta.put("sucesso", false);
            resposta.put(
                    "erro",
                    "Não foi possível ler o arquivo enviado."
            );

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(resposta);
        }
    }

    @PostMapping("/mapeamento/preview")
    public ResponseEntity<Map<String, Object>> visualizarMapeamento(
            @RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam("mapeamento") String mapeamentoJson
    ) {

        Map<String, Object> resposta =
                new LinkedHashMap<>();

        try {

            ImportColumnMappingRequest mapeamento =
                   converterMapeamento(mapeamentoJson);

            List<FinancialTransaction> lancamentos =
                    importMappingService.processar(
                            arquivo,
                            mapeamento
                    );

            resposta.put("sucesso", true);
            resposta.put(
                    "arquivo",
                    arquivo.getOriginalFilename()
            );
            resposta.put(
                    "quantidade",
                    lancamentos.size()
            );
            resposta.put(
                    "lancamentos",
                    lancamentos
            );

            return ResponseEntity.ok(resposta);

        } catch (IllegalArgumentException exception) {

            resposta.put("sucesso", false);
            resposta.put(
                    "erro",
                    exception.getMessage()
            );

            return ResponseEntity
                    .badRequest()
                    .body(resposta);

        } catch (IOException exception) {

            resposta.put("sucesso", false);
            resposta.put(
                    "erro",
                    "Não foi possível ler o arquivo enviado."
            );

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(resposta);
        }
    }

    @PostMapping("/mapeamento/importar")
    public ResponseEntity<Map<String, Object>> importarMapeamento(
            @RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam("mapeamento") String mapeamentoJson
    ) {

        Map<String, Object> resposta =
                new LinkedHashMap<>();

        try {

            ImportColumnMappingRequest mapeamento =
                    converterMapeamento(mapeamentoJson);

            List<FinancialTransaction> processados =
                    importMappingService.processar(
                            arquivo,
                            mapeamento
                    );

            List<FinancialTransaction> importados =
                    financialImportService.salvar(
                            processados
                    );

            int ignorados =
                    processados.size()
                            - importados.size();

            resposta.put("sucesso", true);
            resposta.put(
                    "arquivo",
                    arquivo.getOriginalFilename()
            );
            resposta.put(
                    "processados",
                    processados.size()
            );
            resposta.put(
                    "importados",
                    importados.size()
            );
            resposta.put(
                    "ignoradosDuplicidade",
                    ignorados
            );
            resposta.put(
                    "lancamentos",
                    importados
            );

            return ResponseEntity.ok(resposta);

        } catch (IllegalArgumentException exception) {

            resposta.put("sucesso", false);
            resposta.put(
                    "erro",
                    exception.getMessage()
            );

            return ResponseEntity
                    .badRequest()
                    .body(resposta);

        } catch (IOException exception) {

            resposta.put("sucesso", false);
            resposta.put(
                    "erro",
                    "Não foi possível ler o arquivo enviado."
            );

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(resposta);
        }
    }

    @PostMapping("/csv/preview")
    public ResponseEntity<Map<String, Object>> visualizarCsv(
            @RequestParam("arquivo") MultipartFile arquivo
    ) {

        Map<String, Object> resposta =
                new LinkedHashMap<>();

        try {

            List<FinancialTransaction> lancamentos =
                    csvImportService.processar(arquivo);

            resposta.put("sucesso", true);
            resposta.put(
                    "arquivo",
                    arquivo.getOriginalFilename()
            );
            resposta.put(
                    "quantidade",
                    lancamentos.size()
            );
            resposta.put(
                    "lancamentos",
                    lancamentos
            );

            return ResponseEntity.ok(resposta);

        } catch (IllegalArgumentException exception) {

            resposta.put("sucesso", false);
            resposta.put(
                    "erro",
                    exception.getMessage()
            );

            return ResponseEntity
                    .badRequest()
                    .body(resposta);

        } catch (IOException exception) {

            resposta.put("sucesso", false);
            resposta.put(
                    "erro",
                    "Não foi possível ler o arquivo CSV enviado."
            );

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(resposta);
        }
    }

    @PostMapping("/csv/importar")
    public ResponseEntity<Map<String, Object>> importarCsv(
            @RequestParam("arquivo") MultipartFile arquivo
    ) {

        Map<String, Object> resposta =
                new LinkedHashMap<>();

        try {

            List<FinancialTransaction> processados =
                    csvImportService.processar(arquivo);

            List<FinancialTransaction> importados =
                    financialImportService.salvar(
                            processados
                    );

            int ignorados =
                    processados.size()
                            - importados.size();

            resposta.put("sucesso", true);
            resposta.put(
                    "arquivo",
                    arquivo.getOriginalFilename()
            );
            resposta.put(
                    "processados",
                    processados.size()
            );
            resposta.put(
                    "importados",
                    importados.size()
            );
            resposta.put(
                    "ignoradosDuplicidade",
                    ignorados
            );
            resposta.put(
                    "lancamentos",
                    importados
            );

            return ResponseEntity.ok(resposta);

        } catch (IllegalArgumentException exception) {

            resposta.put("sucesso", false);
            resposta.put(
                    "erro",
                    exception.getMessage()
            );

            return ResponseEntity
                    .badRequest()
                    .body(resposta);

        } catch (IOException exception) {

            resposta.put("sucesso", false);
            resposta.put(
                    "erro",
                    "Não foi possível ler o arquivo CSV enviado."
            );

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(resposta);
        }
    }

    @PostMapping("/excel/preview")
    public ResponseEntity<Map<String, Object>> visualizarExcel(
            @RequestParam("arquivo") MultipartFile arquivo
    ) {

        Map<String, Object> resposta =
                new LinkedHashMap<>();

        try {

            List<FinancialTransaction> lancamentos =
                    excelImportService.processar(arquivo);

            resposta.put("sucesso", true);
            resposta.put(
                    "arquivo",
                    arquivo.getOriginalFilename()
            );
            resposta.put(
                    "quantidade",
                    lancamentos.size()
            );
            resposta.put(
                    "lancamentos",
                    lancamentos
            );

            return ResponseEntity.ok(resposta);

        } catch (IllegalArgumentException exception) {

            resposta.put("sucesso", false);
            resposta.put(
                    "erro",
                    exception.getMessage()
            );

            return ResponseEntity
                    .badRequest()
                    .body(resposta);

        } catch (IOException exception) {

            resposta.put("sucesso", false);
            resposta.put(
                    "erro",
                    "Não foi possível ler o arquivo Excel enviado."
            );

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(resposta);
        }
    }

    @PostMapping("/excel/importar")
    public ResponseEntity<Map<String, Object>> importarExcel(
            @RequestParam("arquivo") MultipartFile arquivo
    ) {

        Map<String, Object> resposta =
                new LinkedHashMap<>();

        try {

            List<FinancialTransaction> processados =
                    excelImportService.processar(arquivo);

            List<FinancialTransaction> importados =
                    financialImportService.salvar(
                            processados
                    );

            int ignorados =
                    processados.size()
                            - importados.size();

            resposta.put("sucesso", true);
            resposta.put(
                    "arquivo",
                    arquivo.getOriginalFilename()
            );
            resposta.put(
                    "processados",
                    processados.size()
            );
            resposta.put(
                    "importados",
                    importados.size()
            );
            resposta.put(
                    "ignoradosDuplicidade",
                    ignorados
            );
            resposta.put(
                    "lancamentos",
                    importados
            );

            return ResponseEntity.ok(resposta);

        } catch (IllegalArgumentException exception) {

            resposta.put("sucesso", false);
            resposta.put(
                    "erro",
                    exception.getMessage()
            );

            return ResponseEntity
                    .badRequest()
                    .body(resposta);

        } catch (IOException exception) {

            resposta.put("sucesso", false);
            resposta.put(
                    "erro",
                    "Não foi possível ler o arquivo Excel enviado."
            );

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(resposta);
        }
    }

    private ImportColumnMappingRequest converterMapeamento(
            String mapeamentoJson
    ) {


        if (
                mapeamentoJson == null
                        || mapeamentoJson.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "O mapeamento das colunas não foi informado."
            );
        }

        try {

            return objectMapper.readValue(
                    mapeamentoJson,
                    ImportColumnMappingRequest.class
            );

        } catch (JsonProcessingException exception) {

            throw new IllegalArgumentException(
                    "O mapeamento informado possui um JSON inválido."
            );
        }
    }
}