package com.finvista.controller;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/fluxo-caixa/exportar")
public class CashFlowExportController {

    private static final int COLUNAS = 7;
    private static final int MAX_LINHAS = 4_000;
    private static final int MAX_TEXTO = 100;

    @PostMapping("/{formato}")
    public ResponseEntity<byte[]> exportar(@PathVariable String formato,
            @RequestBody List<List<JsonNode>> linhas) throws IOException {
        boolean legado = "xls".equals(formato);
        if (!legado && !"xlsx".equals(formato)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato de exportação inválido.");
        }
        validar(linhas);
        try (Workbook livro = legado ? new HSSFWorkbook() : new XSSFWorkbook(); var saida = new ByteArrayOutputStream()) {
            var planilha = livro.createSheet("Fluxo de caixa");
            var dinheiro = livro.createCellStyle();
            dinheiro.setDataFormat(livro.createDataFormat().getFormat("#,##0.00"));
            planilha.createFreezePane(0, 1);
            for (int i = 0; i < linhas.size(); i++) {
                var linha = planilha.createRow(i);
                for (int j = 0; j < COLUNAS; j++) {
                    var valor = linhas.get(i).get(j);
                    var celula = linha.createCell(j);
                    if (valor.isTextual()) {
                        celula.setCellValue(valor.textValue());
                    } else {
                        celula.setCellValue(valor.doubleValue());
                        celula.setCellStyle(dinheiro);
                    }
                }
            }
            for (int j = 0; j < COLUNAS; j++) {
                planilha.setColumnWidth(j, (j == 0 ? 14 : 28) * 256);
            }
            livro.write(saida);
            var mime = legado ? "application/vnd.ms-excel"
                    : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .contentType(MediaType.parseMediaType(mime))
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename("fluxo-financeiro-mensal." + formato).build().toString())
                    .body(saida.toByteArray());
        }
    }

    private void validar(List<List<JsonNode>> linhas) {
        if (linhas == null || linhas.size() < 2 || linhas.size() > MAX_LINHAS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantidade de linhas inválida.");
        }
        for (int i = 0; i < linhas.size(); i++) {
            var linha = linhas.get(i);
            if (linha == null || linha.size() != COLUNAS) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantidade de colunas inválida.");
            }
            for (int j = 0; j < COLUNAS; j++) {
                var valor = linha.get(j);
                boolean texto = i == 0 || j == 0;
                if (valor == null || (texto
                        ? !valor.isTextual() || valor.textValue().length() > MAX_TEXTO
                        : !valor.isNumber() || !Double.isFinite(valor.doubleValue()))) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor inválido para a planilha.");
                }
            }
        }
    }
}
