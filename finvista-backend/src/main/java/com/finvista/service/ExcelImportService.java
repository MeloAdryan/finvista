package com.finvista.service;

import com.finvista.model.FinancialTransaction;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ExcelImportService {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final DataFormatter dataFormatter =
            new DataFormatter(new Locale("pt", "BR"));

    public List<FinancialTransaction> processar(
            MultipartFile arquivo
    ) throws IOException {

        validarArquivo(arquivo);

        List<FinancialTransaction> lancamentos =
                new ArrayList<>();

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

            Map<String, Integer> colunas =
                    mapearColunas(cabecalho);

            validarColunasObrigatorias(colunas);

            int primeiraLinhaDados =
                    cabecalho.getRowNum() + 1;

            for (
                    int numeroLinha = primeiraLinhaDados;
                    numeroLinha <= planilha.getLastRowNum();
                    numeroLinha++
            ) {

                Row linha =
                        planilha.getRow(numeroLinha);

                if (linha == null || linhaVazia(linha)) {
                    continue;
                }

                FinancialTransaction lancamento =
                        converterLinha(
                                linha,
                                colunas
                        );

                lancamentos.add(lancamento);
            }
        }

        return lancamentos;
    }

    private void validarArquivo(
            MultipartFile arquivo
    ) {

        if (arquivo == null || arquivo.isEmpty()) {
            throw new IllegalArgumentException(
                    "O arquivo Excel está vazio."
            );
        }

        String nomeArquivo =
                arquivo.getOriginalFilename();

        if (
                nomeArquivo == null
                        || !nomeArquivo
                        .toLowerCase(Locale.ROOT)
                        .endsWith(".xlsx")
        ) {
            throw new IllegalArgumentException(
                    "O arquivo deve estar no formato XLSX."
            );
        }
    }

    private Map<String, Integer> mapearColunas(
            Row cabecalho
    ) {

        Map<String, Integer> colunas =
                new HashMap<>();

        for (Cell celula : cabecalho) {

            String nome =
                    dataFormatter
                            .formatCellValue(celula)
                            .trim()
                            .toLowerCase(Locale.ROOT);

            if (!nome.isBlank()) {
                colunas.put(
                        nome,
                        celula.getColumnIndex()
                );
            }
        }

        return colunas;
    }

    private void validarColunasObrigatorias(
            Map<String, Integer> colunas
    ) {

        String[] obrigatorias = {
                "data",
                "descricao",
                "tipo",
                "valor"
        };

        for (String coluna : obrigatorias) {

            if (!colunas.containsKey(coluna)) {
                throw new IllegalArgumentException(
                        "Coluna obrigatória não encontrada: "
                                + coluna
                );
            }
        }
    }

    private FinancialTransaction converterLinha(
            Row linha,
            Map<String, Integer> colunas
    ) {

        LocalDate data =
                converterData(
                        linha,
                        colunas.get("data"),
                        linha.getRowNum() + 1
                );

        String descricao =
                obterCampoObrigatorio(
                        linha,
                        colunas,
                        "descricao"
                );

        String tipo =
                converterTipo(
                        obterCampoObrigatorio(
                                linha,
                                colunas,
                                "tipo"
                        ),
                        linha.getRowNum() + 1
                );

        BigDecimal valor =
                converterValor(
                        linha,
                        colunas.get("valor"),
                        linha.getRowNum() + 1
                );

        String categoria =
                obterCampoOpcional(
                        linha,
                        colunas,
                        "categoria"
                );

        String centroCusto =
                obterCampoOpcional(
                        linha,
                        colunas,
                        "centro_custo"
                );

        String documentoReferencia =
                obterCampoOpcional(
                        linha,
                        colunas,
                        "documento_referencia"
                );

        return new FinancialTransaction(
                data,
                descricao,
                tipo,
                valor,
                categoria,
                centroCusto,
                "EXCEL",
                documentoReferencia
        );
    }

    private String obterCampoObrigatorio(
            Row linha,
            Map<String, Integer> colunas,
            String coluna
    ) {

        Integer indice =
                colunas.get(coluna);

        if (indice == null) {
            throw new IllegalArgumentException(
                    "Coluna obrigatória não encontrada: "
                            + coluna
            );
        }

        String valor =
                obterTexto(
                        linha.getCell(
                                indice,
                                Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
                        )
                );

        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    "Campo obrigatório vazio na linha "
                            + (linha.getRowNum() + 1)
                            + ": "
                            + coluna
            );
        }

        return valor.trim();
    }

    private String obterCampoOpcional(
            Row linha,
            Map<String, Integer> colunas,
            String coluna
    ) {

        Integer indice =
                colunas.get(coluna);

        if (indice == null) {
            return null;
        }

        String valor =
                obterTexto(
                        linha.getCell(
                                indice,
                                Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
                        )
                );

        if (valor == null || valor.isBlank()) {
            return null;
        }

        return valor.trim();
    }

    private String obterTexto(
            Cell celula
    ) {

        if (celula == null) {
            return null;
        }

        return dataFormatter
                .formatCellValue(celula)
                .trim();
    }

    private LocalDate converterData(
            Row linha,
            Integer indice,
            int numeroLinha
    ) {

        if (indice == null) {
            throw new IllegalArgumentException(
                    "Coluna obrigatória não encontrada: data"
            );
        }

        Cell celula =
                linha.getCell(
                        indice,
                        Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
                );

        if (celula == null) {
            throw new IllegalArgumentException(
                    "Campo obrigatório vazio na linha "
                            + numeroLinha
                            + ": data"
            );
        }

        if (
                celula.getCellType() == CellType.NUMERIC
                        && DateUtil.isCellDateFormatted(celula)
        ) {

            return celula
                    .getDateCellValue()
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
        }

        String valor =
                obterTexto(celula);

        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    "Campo obrigatório vazio na linha "
                            + numeroLinha
                            + ": data"
            );
        }

        try {

            return LocalDate.parse(
                    valor,
                    DATE_FORMATTER
            );

        } catch (DateTimeParseException exception) {

            throw new IllegalArgumentException(
                    "Data inválida na linha "
                            + numeroLinha
                            + ": "
                            + valor
                            + ". Use uma data válida do Excel ou o formato dd/MM/yyyy."
            );
        }
    }

    private String converterTipo(
            String valor,
            int numeroLinha
    ) {

        String tipo =
                valor.trim()
                        .toUpperCase(Locale.ROOT);

        if (
                !tipo.equals("RECEITA")
                        && !tipo.equals("DESPESA")
        ) {
            throw new IllegalArgumentException(
                    "Tipo inválido na linha "
                            + numeroLinha
                            + ": "
                            + valor
                            + ". Use RECEITA ou DESPESA."
            );
        }

        return tipo;
    }

    private BigDecimal converterValor(
            Row linha,
            Integer indice,
            int numeroLinha
    ) {

        if (indice == null) {
            throw new IllegalArgumentException(
                    "Coluna obrigatória não encontrada: valor"
            );
        }

        Cell celula =
                linha.getCell(
                        indice,
                        Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
                );

        if (celula == null) {
            throw new IllegalArgumentException(
                    "Campo obrigatório vazio na linha "
                            + numeroLinha
                            + ": valor"
            );
        }

        if (celula.getCellType() == CellType.NUMERIC) {

            BigDecimal valor =
                    BigDecimal.valueOf(
                            celula.getNumericCellValue()
                    );

            validarValorNaoNegativo(
                    valor,
                    numeroLinha
            );

            return valor;
        }

        String texto =
                obterTexto(celula);

        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException(
                    "Campo obrigatório vazio na linha "
                            + numeroLinha
                            + ": valor"
            );
        }

        try {

            String valorNormalizado =
                    texto
                            .replace("R$", "")
                            .replace(" ", "")
                            .trim();

            if (
                    valorNormalizado.contains(".")
                            && valorNormalizado.contains(",")
            ) {

                valorNormalizado =
                        valorNormalizado
                                .replace(".", "")
                                .replace(",", ".");

            } else if (
                    valorNormalizado.contains(",")
            ) {

                valorNormalizado =
                        valorNormalizado
                                .replace(",", ".");
            }

            BigDecimal valor =
                    new BigDecimal(
                            valorNormalizado
                    );

            validarValorNaoNegativo(
                    valor,
                    numeroLinha
            );

            return valor;

        } catch (NumberFormatException exception) {

            throw new IllegalArgumentException(
                    "Valor financeiro inválido na linha "
                            + numeroLinha
                            + ": "
                            + texto
            );
        }
    }

    private void validarValorNaoNegativo(
            BigDecimal valor,
            int numeroLinha
    ) {

        if (
                valor.compareTo(
                        BigDecimal.ZERO
                ) < 0
        ) {
            throw new IllegalArgumentException(
                    "O valor não pode ser negativo na linha "
                            + numeroLinha
            );
        }
    }

    private boolean linhaVazia(
            Row linha
    ) {

        for (
                int indice = linha.getFirstCellNum();
                indice < linha.getLastCellNum();
                indice++
        ) {

            if (indice < 0) {
                continue;
            }

            Cell celula =
                    linha.getCell(
                            indice,
                            Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
                    );

            String valor =
                    obterTexto(celula);

            if (valor != null && !valor.isBlank()) {
                return false;
            }
        }

        return true;
    }
}