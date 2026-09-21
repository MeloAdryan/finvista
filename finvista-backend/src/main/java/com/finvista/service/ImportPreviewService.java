package com.finvista.service;

import com.finvista.dto.importacao.ImportPreviewResponse;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ImportPreviewService {

    private static final int LIMITE_PREVIEW = 10;

    private final DataFormatter dataFormatter =
            new DataFormatter(new Locale("pt", "BR"));

    public ImportPreviewResponse gerarPreview(
            MultipartFile arquivo
    ) throws IOException {

        validarArquivo(arquivo);

        String nomeArquivo =
                arquivo.getOriginalFilename();

        String nomeNormalizado =
                nomeArquivo
                        .toLowerCase(Locale.ROOT);

        if (nomeNormalizado.endsWith(".csv")) {
            return gerarPreviewCsv(arquivo);
        }

        if (nomeNormalizado.endsWith(".xlsx")) {
            return gerarPreviewExcel(arquivo);
        }

        throw new IllegalArgumentException(
                "Formato de arquivo não suportado."
        );
    }

    private ImportPreviewResponse gerarPreviewCsv(
            MultipartFile arquivo
    ) throws IOException {

        List<String> colunas =
                new ArrayList<>();

        List<Map<String, String>> linhas =
                new ArrayList<>();

        int quantidadeLinhas = 0;

        String conteudo =
                new String(
                        arquivo.getBytes(),
                        StandardCharsets.UTF_8
                );

        conteudo = removerBom(conteudo);

        char delimitador =
                detectarDelimitador(conteudo);

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new StringReader(conteudo)
                        );

                CSVParser parser =
                        CSVFormat.DEFAULT
                                .builder()
                                .setDelimiter(delimitador)
                                .setHeader()
                                .setSkipHeaderRecord(true)
                                .setTrim(true)
                                .get()
                                .parse(reader)
        ) {

            colunas.addAll(
                    parser
                            .getHeaderMap()
                            .keySet()
            );

            validarCabecalho(colunas);

            for (CSVRecord registro : parser) {

                if (registroVazio(registro, colunas)) {
                    continue;
                }

                quantidadeLinhas++;

                if (linhas.size() >= LIMITE_PREVIEW) {
                    continue;
                }

                Map<String, String> linha =
                        new LinkedHashMap<>();

                for (String coluna : colunas) {

                    String valor = "";

                    if (registro.isMapped(coluna)) {

                        String valorRegistro =
                                registro.get(coluna);

                        if (valorRegistro != null) {
                            valor =
                                    valorRegistro.trim();
                        }
                    }

                    linha.put(
                            coluna,
                            valor
                    );
                }

                linhas.add(linha);
            }
        }

        return new ImportPreviewResponse(
                arquivo.getOriginalFilename(),
                "CSV",
                colunas,
                linhas,
                quantidadeLinhas
        );
    }

    private String removerBom(
            String conteudo
    ) {

        if (
                conteudo != null
                        && !conteudo.isEmpty()
                        && conteudo.charAt(0) == '\uFEFF'
        ) {
            return conteudo.substring(1);
        }

        return conteudo;
    }

    private char detectarDelimitador(
            String conteudo
    ) {

        if (conteudo == null || conteudo.isBlank()) {
            throw new IllegalArgumentException(
                    "O arquivo CSV está vazio."
            );
        }

        String primeiraLinha =
                conteudo.lines()
                        .findFirst()
                        .orElse("");

        if (primeiraLinha.isBlank()) {
            throw new IllegalArgumentException(
                    "O arquivo CSV não possui cabeçalho."
            );
        }

        int quantidadeVirgulas =
                contarDelimitador(
                        primeiraLinha,
                        ','
                );

        int quantidadePontoVirgula =
                contarDelimitador(
                        primeiraLinha,
                        ';'
                );

        if (
                quantidadeVirgulas == 0
                        && quantidadePontoVirgula == 0
        ) {
            throw new IllegalArgumentException(
                    "Não foi possível identificar o separador do CSV. "
                            + "Use vírgula (,) ou ponto e vírgula (;)."
            );
        }

        if (
                quantidadePontoVirgula
                        > quantidadeVirgulas
        ) {
            return ';';
        }

        return ',';
    }

    private int contarDelimitador(
            String linha,
            char delimitador
    ) {

        int quantidade = 0;
        boolean dentroDeAspas = false;

        for (
                int indice = 0;
                indice < linha.length();
                indice++
        ) {

            char caractere =
                    linha.charAt(indice);

            if (caractere == '"') {

                if (
                        dentroDeAspas
                                && indice + 1 < linha.length()
                                && linha.charAt(indice + 1) == '"'
                ) {
                    indice++;
                    continue;
                }

                dentroDeAspas =
                        !dentroDeAspas;

                continue;
            }

            if (
                    caractere == delimitador
                            && !dentroDeAspas
            ) {
                quantidade++;
            }
        }

        return quantidade;
    }

    private ImportPreviewResponse gerarPreviewExcel(
            MultipartFile arquivo
    ) throws IOException {

        List<String> colunas =
                new ArrayList<>();

        List<Map<String, String>> linhas =
                new ArrayList<>();

        int quantidadeLinhas = 0;

        try (
                Workbook workbook =
                        new XSSFWorkbook(
                                arquivo.getInputStream()
                        )
        ) {

            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException(
                        "O arquivo Excel não possui planilhas."
                );
            }

            Sheet planilha =
                    workbook.getSheetAt(0);

            Row cabecalho =
                    planilha.getRow(
                            planilha.getFirstRowNum()
                    );

            if (cabecalho == null) {
                throw new IllegalArgumentException(
                        "O arquivo Excel não possui cabeçalho."
                );
            }

            Map<Integer, String> colunasPorIndice =
                    extrairCabecalhoExcel(cabecalho);

            colunas.addAll(
                    colunasPorIndice.values()
            );

            validarCabecalho(colunas);

            int primeiraLinhaDados =
                    cabecalho.getRowNum() + 1;

            for (
                    int numeroLinha = primeiraLinhaDados;
                    numeroLinha <= planilha.getLastRowNum();
                    numeroLinha++
            ) {

                Row linhaExcel =
                        planilha.getRow(numeroLinha);

                if (
                        linhaExcel == null
                                || linhaExcelVazia(
                                linhaExcel,
                                colunasPorIndice
                        )
                ) {
                    continue;
                }

                quantidadeLinhas++;

                if (linhas.size() >= LIMITE_PREVIEW) {
                    continue;
                }

                Map<String, String> linha =
                        new LinkedHashMap<>();

                for (
                        Map.Entry<Integer, String> coluna :
                                colunasPorIndice.entrySet()
                ) {

                    Cell celula =
                            linhaExcel.getCell(
                                    coluna.getKey(),
                                    Row.MissingCellPolicy
                                            .RETURN_BLANK_AS_NULL
                            );

                    String valor =
                            formatarCelula(celula);

                    linha.put(
                            coluna.getValue(),
                            valor
                    );
                }

                linhas.add(linha);
            }
        }

        return new ImportPreviewResponse(
                arquivo.getOriginalFilename(),
                "EXCEL",
                colunas,
                linhas,
                quantidadeLinhas
        );
    }

    private Map<Integer, String> extrairCabecalhoExcel(
            Row cabecalho
    ) {

        Map<Integer, String> colunas =
                new LinkedHashMap<>();

        for (
                int indice = cabecalho.getFirstCellNum();
                indice < cabecalho.getLastCellNum();
                indice++
        ) {

            if (indice < 0) {
                continue;
            }

            Cell celula =
                    cabecalho.getCell(
                            indice,
                            Row.MissingCellPolicy
                                    .RETURN_BLANK_AS_NULL
                    );

            String nome =
                    formatarCelula(celula);

            if (nome.isBlank()) {
                continue;
            }

            if (colunas.containsValue(nome)) {
                throw new IllegalArgumentException(
                        "O arquivo possui colunas duplicadas: "
                                + nome
                );
            }

            colunas.put(
                    indice,
                    nome
            );
        }

        return colunas;
    }

    private void validarArquivo(
            MultipartFile arquivo
    ) {

        if (arquivo == null || arquivo.isEmpty()) {
            throw new IllegalArgumentException(
                    "O arquivo enviado está vazio."
            );
        }

        String nomeArquivo =
                arquivo.getOriginalFilename();

        if (
                nomeArquivo == null
                        || nomeArquivo.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "O arquivo enviado não possui um nome válido."
            );
        }

        String nomeNormalizado =
                nomeArquivo
                        .toLowerCase(Locale.ROOT);

        if (
                !nomeNormalizado.endsWith(".csv")
                        && !nomeNormalizado.endsWith(".xlsx")
        ) {
            throw new IllegalArgumentException(
                    "Formato não suportado. Envie um arquivo CSV ou XLSX."
            );
        }
    }

    private void validarCabecalho(
            List<String> colunas
    ) {

        if (colunas == null || colunas.isEmpty()) {
            throw new IllegalArgumentException(
                    "O arquivo não possui colunas no cabeçalho."
            );
        }

        for (String coluna : colunas) {

            if (coluna == null || coluna.isBlank()) {
                throw new IllegalArgumentException(
                        "O arquivo possui uma coluna sem nome."
                );
            }
        }

        long quantidadeUnica =
                colunas
                        .stream()
                        .distinct()
                        .count();

        if (quantidadeUnica != colunas.size()) {
            throw new IllegalArgumentException(
                    "O arquivo possui nomes de colunas duplicados."
            );
        }
    }

    private boolean registroVazio(
            CSVRecord registro,
            List<String> colunas
    ) {

        for (String coluna : colunas) {

            if (!registro.isMapped(coluna)) {
                continue;
            }

            String valor =
                    registro.get(coluna);

            if (
                    valor != null
                            && !valor.isBlank()
            ) {
                return false;
            }
        }

        return true;
    }

    private boolean linhaExcelVazia(
            Row linha,
            Map<Integer, String> colunas
    ) {

        for (Integer indice : colunas.keySet()) {

            Cell celula =
                    linha.getCell(
                            indice,
                            Row.MissingCellPolicy
                                    .RETURN_BLANK_AS_NULL
                    );

            String valor =
                    formatarCelula(celula);

            if (!valor.isBlank()) {
                return false;
            }
        }

        return true;
    }

    private String formatarCelula(
            Cell celula
    ) {

        if (celula == null) {
            return "";
        }

        return dataFormatter
                .formatCellValue(celula)
                .trim();
    }
}