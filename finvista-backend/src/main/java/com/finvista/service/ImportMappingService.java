package com.finvista.service;

import com.finvista.dto.importacao.ImportColumnMappingRequest;
import com.finvista.model.FinancialTransaction;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ImportMappingService {

    private static final DateTimeFormatter DATE_FORMATTER =
        new DateTimeFormatterBuilder()
                .appendPattern("dd/MM/uuuu")
                .toFormatter(Locale.ROOT)
                .withResolverStyle(ResolverStyle.STRICT);

    private final DataFormatter dataFormatter =
            new DataFormatter(new Locale("pt", "BR"));

    private final FinancialDataNormalizationService normalizationService;

    private final FinancialTransactionValidationService validationService;

    public ImportMappingService(
            FinancialDataNormalizationService normalizationService,
            FinancialTransactionValidationService validationService
    ) {
        this.normalizationService = normalizationService;
        this.validationService = validationService;
    }

    public List<FinancialTransaction> processar(
            MultipartFile arquivo,
            ImportColumnMappingRequest mapeamento
    ) throws IOException {

        validarArquivo(arquivo);
        validarMapeamento(mapeamento);

        String nomeArquivo =
                arquivo.getOriginalFilename();

        String nomeNormalizado =
                nomeArquivo.toLowerCase(Locale.ROOT);

        if (nomeNormalizado.endsWith(".csv")) {
            return processarCsv(
                    arquivo,
                    mapeamento
            );
        }

        if (nomeNormalizado.endsWith(".xlsx")) {
            return processarExcel(
                    arquivo,
                    mapeamento
            );
        }

        throw new IllegalArgumentException(
                "Formato não suportado. Envie um arquivo CSV ou XLSX."
        );
    }

    private List<FinancialTransaction> processarCsv(
        MultipartFile arquivo,
        ImportColumnMappingRequest mapeamento
) throws IOException {

    List<FinancialTransaction> lancamentos =
            new ArrayList<>();

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

        List<String> cabecalhos =
                parser.getHeaderNames();

        validarColunasMapeadas(
                cabecalhos,
                mapeamento
        );

        for (CSVRecord registro : parser) {

            if (registroVazio(registro)) {
                continue;
            }

            int numeroLinha =
                    (int) registro.getRecordNumber() + 1;

            FinancialTransaction lancamento =
                    converterCsv(
                            registro,
                            mapeamento,
                            numeroLinha
                    );

            lancamentos.add(
                    lancamento
            );
        }
    }

    return lancamentos;
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
    
private FinancialTransaction converterCsv(
            CSVRecord registro,
            ImportColumnMappingRequest mapeamento,
            int numeroLinha
    ) {

        String dataTexto =
                obterValorCsv(
                        registro,
                        mapeamento.getData()
                );

        String descricao =
                obterValorCsv(
                        registro,
                        mapeamento.getDescricao()
                );

        String tipo =
                obterValorCsv(
                        registro,
                        mapeamento.getTipo()
                );

        String valorTexto =
                obterValorCsv(
                        registro,
                        mapeamento.getValor()
                );

        validarCampoObrigatorio(
                dataTexto,
                "data",
                numeroLinha
        );

        validarCampoObrigatorio(
                descricao,
                "descricao",
                numeroLinha
        );

        validarCampoObrigatorio(
                tipo,
                "tipo",
                numeroLinha
        );

        validarCampoObrigatorio(
                valorTexto,
                "valor",
                numeroLinha
        );

        FinancialTransaction lancamento =
                new FinancialTransaction();

        lancamento.setData(
        validationService.validarData(
                converterDataTexto(
                        dataTexto,
                        numeroLinha
                ),
                numeroLinha
        )
);

        lancamento.setDescricao(
        validationService.validarESanearDescricao(
                descricao,
                numeroLinha
        )
);

        lancamento.setTipo(
                converterTipo(
                        tipo,
                        numeroLinha
                )
        );

       lancamento.setValor(
        validationService.validarValor(
                converterValor(
                        valorTexto,
                        numeroLinha
                ),
                numeroLinha
        )
);

        lancamento.setCategoria(
                normalizationService.normalizarCategoria(
                        obterValorCsvOpcional(
                                registro,
                                mapeamento.getCategoria()
                        )
                )
        );

        lancamento.setCentroCusto(
                normalizationService.normalizarCentroCusto(
                        obterValorCsvOpcional(
                                registro,
                                mapeamento.getCentroCusto()
                        )
                )
        );

      lancamento.setDocumentoReferencia(
        validationService.validarESanearDocumentoReferencia(
                obterValorCsvOpcional(
                        registro,
                        mapeamento.getDocumentoReferencia()
                ),
                numeroLinha
        )
);

        lancamento.setOrigem("CSV");

validationService.validarLancamento(
        lancamento,
        numeroLinha
);

return lancamento;
    }
    private List<FinancialTransaction> processarExcel(
            MultipartFile arquivo,
            ImportColumnMappingRequest mapeamento
    ) throws IOException {

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

            Map<String, Integer> indices =
                    obterIndicesExcel(
                            cabecalho
                    );

            validarColunasMapeadas(
                    new ArrayList<>(
                            indices.keySet()
                    ),
                    mapeamento
            );

            for (
                    int indiceLinha =
                            cabecalho.getRowNum() + 1;

                    indiceLinha <=
                            planilha.getLastRowNum();

                    indiceLinha++
            ) {

                Row linha =
                        planilha.getRow(
                                indiceLinha
                        );

                if (
                        linha == null
                                || linhaExcelVazia(
                                        linha,
                                        indices
                                )
                ) {
                    continue;
                }

                FinancialTransaction lancamento =
                        converterExcel(
                                linha,
                                indices,
                                mapeamento
                        );

                lancamentos.add(
                        lancamento
                );
            }
        }

        return lancamentos;
    }

    private FinancialTransaction converterExcel(
            Row linha,
            Map<String, Integer> indices,
            ImportColumnMappingRequest mapeamento
    ) {

        int numeroLinha =
                linha.getRowNum() + 1;

        Cell celulaData =
                obterCelulaExcel(
                        linha,
                        indices,
                        mapeamento.getData()
                );

        String descricao =
                obterValorExcel(
                        linha,
                        indices,
                        mapeamento.getDescricao()
                );

        String tipo =
                obterValorExcel(
                        linha,
                        indices,
                        mapeamento.getTipo()
                );

        String valorTexto =
                obterValorExcel(
                        linha,
                        indices,
                        mapeamento.getValor()
                );

        validarCampoObrigatorio(
                descricao,
                "descricao",
                numeroLinha
        );

        validarCampoObrigatorio(
                tipo,
                "tipo",
                numeroLinha
        );

        validarCampoObrigatorio(
                valorTexto,
                "valor",
                numeroLinha
        );

        FinancialTransaction lancamento =
                new FinancialTransaction();

       lancamento.setData(
        validationService.validarData(
                converterDataExcel(
                        celulaData,
                        numeroLinha
                ),
                numeroLinha
        )
);

        lancamento.setDescricao(
        validationService.validarESanearDescricao(
                descricao,
                numeroLinha
        )
);

        lancamento.setTipo(
                converterTipo(
                        tipo,
                        numeroLinha
                )
        );

      lancamento.setValor(
        validationService.validarValor(
                converterValor(
                        valorTexto,
                        numeroLinha
                ),
                numeroLinha
        )
);

        lancamento.setCategoria(
                normalizationService.normalizarCategoria(
                        obterValorExcelOpcional(
                                linha,
                                indices,
                                mapeamento.getCategoria()
                        )
                )
        );

        lancamento.setCentroCusto(
                normalizationService.normalizarCentroCusto(
                        obterValorExcelOpcional(
                                linha,
                                indices,
                                mapeamento.getCentroCusto()
                        )
                )
        );

       lancamento.setDocumentoReferencia(
        validationService.validarESanearDocumentoReferencia(
                obterValorExcelOpcional(
                        linha,
                        indices,
                        mapeamento.getDocumentoReferencia()
                ),
                numeroLinha
        )
);

       lancamento.setOrigem("EXCEL");

validationService.validarLancamento(
        lancamento,
        numeroLinha
);

return lancamento;
    }

    private Map<String, Integer> obterIndicesExcel(
            Row cabecalho
    ) {

        Map<String, Integer> indices =
                new LinkedHashMap<>();

        for (
                int indice =
                        cabecalho.getFirstCellNum();

                indice <
                        cabecalho.getLastCellNum();

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
                    formatarCelula(
                            celula
                    );

            if (nome.isBlank()) {
                continue;
            }

            if (indices.containsKey(nome)) {
                throw new IllegalArgumentException(
                        "O arquivo possui colunas duplicadas: "
                                + nome
                );
            }

            indices.put(
                    nome,
                    indice
            );
        }

        return indices;
    }

    private void validarMapeamento(
            ImportColumnMappingRequest mapeamento
    ) {

        if (mapeamento == null) {
            throw new IllegalArgumentException(
                    "O mapeamento das colunas não foi informado."
            );
        }

        validarColunaObrigatoriaMapeamento(
                mapeamento.getData(),
                "data"
        );

        validarColunaObrigatoriaMapeamento(
                mapeamento.getDescricao(),
                "descricao"
        );

        validarColunaObrigatoriaMapeamento(
                mapeamento.getTipo(),
                "tipo"
        );

        validarColunaObrigatoriaMapeamento(
                mapeamento.getValor(),
                "valor"
        );
    }

    private void validarColunaObrigatoriaMapeamento(
            String coluna,
            String campo
    ) {

        if (
                coluna == null
                        || coluna.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "O campo obrigatório '"
                            + campo
                            + "' não possui uma coluna mapeada."
            );
        }
    }

    private void validarColunasMapeadas(
            List<String> cabecalhos,
            ImportColumnMappingRequest mapeamento
    ) {

        validarColunaExistente(
                cabecalhos,
                mapeamento.getData(),
                "data"
        );

        validarColunaExistente(
                cabecalhos,
                mapeamento.getDescricao(),
                "descricao"
        );

        validarColunaExistente(
                cabecalhos,
                mapeamento.getTipo(),
                "tipo"
        );

        validarColunaExistente(
                cabecalhos,
                mapeamento.getValor(),
                "valor"
        );

        validarColunaOpcionalExistente(
                cabecalhos,
                mapeamento.getCategoria(),
                "categoria"
        );

        validarColunaOpcionalExistente(
                cabecalhos,
                mapeamento.getCentroCusto(),
                "centroCusto"
        );

        validarColunaOpcionalExistente(
                cabecalhos,
                mapeamento.getDocumentoReferencia(),
                "documentoReferencia"
        );
    }

    private void validarColunaExistente(
            List<String> cabecalhos,
            String coluna,
            String campo
    ) {

        if (!cabecalhos.contains(coluna)) {
            throw new IllegalArgumentException(
                    "A coluna '"
                            + coluna
                            + "' mapeada para '"
                            + campo
                            + "' não existe no arquivo."
            );
        }
    }

    private void validarColunaOpcionalExistente(
            List<String> cabecalhos,
            String coluna,
            String campo
    ) {

        if (
                coluna == null
                        || coluna.isBlank()
        ) {
            return;
        }

        validarColunaExistente(
                cabecalhos,
                coluna,
                campo
        );
    }

    private String obterValorCsv(
            CSVRecord registro,
            String coluna
    ) {

        if (!registro.isMapped(coluna)) {
            return "";
        }

        String valor =
                registro.get(
                        coluna
                );

        return valor == null
                ? ""
                : valor.trim();
    }

    private String obterValorCsvOpcional(
            CSVRecord registro,
            String coluna
    ) {

        if (
                coluna == null
                        || coluna.isBlank()
        ) {
            return null;
        }

        String valor =
                obterValorCsv(
                        registro,
                        coluna
                );

        return valor.isBlank()
                ? null
                : valor;
    }

    private boolean registroVazio(
            CSVRecord registro
    ) {

        for (String valor : registro) {

            if (
                    valor != null
                            && !valor.isBlank()
            ) {
                return false;
            }
        }

        return true;
    }

    private Cell obterCelulaExcel(
            Row linha,
            Map<String, Integer> indices,
            String coluna
    ) {

        Integer indice =
                indices.get(
                        coluna
                );

        if (indice == null) {
            return null;
        }

        return linha.getCell(
                indice,
                Row.MissingCellPolicy
                        .RETURN_BLANK_AS_NULL
        );
    }

    private String obterValorExcel(
            Row linha,
            Map<String, Integer> indices,
            String coluna
    ) {

        return formatarCelula(
                obterCelulaExcel(
                        linha,
                        indices,
                        coluna
                )
        );
    }

    private String obterValorExcelOpcional(
            Row linha,
            Map<String, Integer> indices,
            String coluna
    ) {

        if (
                coluna == null
                        || coluna.isBlank()
        ) {
            return null;
        }

        String valor =
                obterValorExcel(
                        linha,
                        indices,
                        coluna
                );

        return valor.isBlank()
                ? null
                : valor;
    }

    private boolean linhaExcelVazia(
            Row linha,
            Map<String, Integer> indices
    ) {

        for (Integer indice : indices.values()) {

            Cell celula =
                    linha.getCell(
                            indice,
                            Row.MissingCellPolicy
                                    .RETURN_BLANK_AS_NULL
                    );

            if (
                    !formatarCelula(
                            celula
                    ).isBlank()
            ) {
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
                .formatCellValue(
                        celula
                )
                .trim();
    }

    private LocalDate converterDataExcel(
            Cell celula,
            int numeroLinha
    ) {

        if (celula == null) {
            throw new IllegalArgumentException(
                    "Campo obrigatório vazio na linha "
                            + numeroLinha
                            + ": data"
            );
        }

        if (
                celula.getCellType()
                        == CellType.NUMERIC
                        && DateUtil
                        .isCellDateFormatted(
                                celula
                        )
        ) {

            return celula
                    .getDateCellValue()
                    .toInstant()
                    .atZone(
                            ZoneId.systemDefault()
                    )
                    .toLocalDate();
        }

        String valor =
                formatarCelula(
                        celula
                );

        return converterDataTexto(
                valor,
                numeroLinha
        );
    }

    private LocalDate converterDataTexto(
            String valor,
            int numeroLinha
    ) {

        if (
                valor == null
                        || valor.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Campo obrigatório vazio na linha "
                            + numeroLinha
                            + ": data"
            );
        }

        try {

            return LocalDate.parse(
                    valor.trim(),
                    DATE_FORMATTER
            );

        } catch (DateTimeParseException exception) {

            throw new IllegalArgumentException(
                    "Data inválida na linha "
                            + numeroLinha
                            + ": "
                            + valor
                            + ". Use o formato dd/MM/yyyy."
            );
        }
    }

    private String converterTipo(
        String valor,
        int numeroLinha
) {

    if (valor == null || valor.isBlank()) {
        throw new IllegalArgumentException(
                "Campo obrigatório vazio na linha "
                        + numeroLinha
                        + ": tipo"
        );
    }

    String tipo =
            valor
                    .trim()
                    .toUpperCase(Locale.ROOT);

    tipo =
            java.text.Normalizer
                    .normalize(
                            tipo,
                            java.text.Normalizer.Form.NFD
                    )
                    .replaceAll("\\p{M}", "");

    return switch (tipo) {

        case "RECEITA",
             "ENTRADA",
             "RECEBIMENTO",
             "CREDITO" -> "RECEITA";

        case "DESPESA",
             "SAIDA",
             "PAGAMENTO",
             "DEBITO" -> "DESPESA";

        default -> throw new IllegalArgumentException(
                "Tipo financeiro inválido na linha "
                        + numeroLinha
                        + ": "
                        + valor
                        + ". Valores aceitos: "
                        + "RECEITA, ENTRADA, RECEBIMENTO, CRÉDITO, "
                        + "DESPESA, SAÍDA, PAGAMENTO ou DÉBITO."
        );
    };
}

    private BigDecimal converterValor(
            String valor,
            int numeroLinha
    ) {

        if (
                valor == null
                        || valor.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Campo obrigatório vazio na linha "
                            + numeroLinha
                            + ": valor"
            );
        }

        String normalizado =
                valor
                        .replace("R$", "")
                        .replace("\u00A0", "")
                        .replace(" ", "")
                        .trim();

        if (
                normalizado.contains(".")
                        && normalizado.contains(",")
        ) {

            if (
                    normalizado.lastIndexOf(",")
                            > normalizado.lastIndexOf(".")
            ) {

                normalizado =
                        normalizado
                                .replace(".", "")
                                .replace(",", ".");

            } else {

                normalizado =
                        normalizado
                                .replace(",", "");
            }

        } else if (
                normalizado.contains(",")
        ) {

            normalizado =
                    normalizado.replace(
                            ",",
                            "."
                    );
        }

        try {

            BigDecimal numero =
                    new BigDecimal(
                            normalizado
                    );

            if (numero.signum() < 0) {
                throw new IllegalArgumentException(
                        "Valor negativo não permitido na linha "
                                + numeroLinha
                                + ": "
                                + valor
                );
            }

            return numero;

        } catch (NumberFormatException exception) {

            throw new IllegalArgumentException(
                    "Valor inválido na linha "
                            + numeroLinha
                            + ": "
                            + valor
            );
        }
    }

    private void validarCampoObrigatorio(
            String valor,
            String campo,
            int numeroLinha
    ) {

        if (
                valor == null
                        || valor.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Campo obrigatório vazio na linha "
                            + numeroLinha
                            + ": "
                            + campo
            );
        }
    }

    private void validarArquivo(
            MultipartFile arquivo
    ) {

        if (
                arquivo == null
                        || arquivo.isEmpty()
        ) {
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
                nomeArquivo.toLowerCase(
                        Locale.ROOT
                );

        if (
                !nomeNormalizado.endsWith(".csv")
                        && !nomeNormalizado.endsWith(".xlsx")
        ) {
            throw new IllegalArgumentException(
                    "Formato não suportado. Envie um arquivo CSV ou XLSX."
            );
        }
    }
}