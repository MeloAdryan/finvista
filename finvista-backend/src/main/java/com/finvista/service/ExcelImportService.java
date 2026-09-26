package com.finvista.service;

import com.finvista.model.FinancialTransaction;

import org.apache.poi.ss.usermodel.Cell;

import org.apache.poi.ss.usermodel.CellType;

import org.apache.poi.ss.usermodel.DataFormatter;

import org.apache.poi.ss.usermodel.DateUtil;

import org.apache.poi.ss.usermodel.Row;

import org.apache.poi.ss.usermodel.Sheet;

import org.apache.poi.ss.usermodel.Workbook;

import org.apache.poi.ss.usermodel.WorkbookFactory;

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

    private enum TipoLayout {

        FINVISTA_PADRAO,
        CONTA_AZUL_RECEBER,
        CONTA_AZUL_PAGAR

    }

    private static final DateTimeFormatter DATE_FORMATTER
            = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final DataFormatter dataFormatter
            = new DataFormatter(new Locale("pt", "BR"));

    public List<FinancialTransaction> processar(
            MultipartFile arquivo
    ) throws IOException {

        validarArquivo(arquivo);

        List<FinancialTransaction> lancamentos
                = new ArrayList<>();

        try (
                Workbook workbook
                = WorkbookFactory.create(
                        arquivo.getInputStream()
                )) {

                    if (workbook.getNumberOfSheets() == 0) {

                        throw new IllegalArgumentException(
                                "O arquivo Excel não possui planilhas."
                        );

                    }

                    Sheet planilha
                            = workbook.getSheetAt(0);

                    Row cabecalho
                            = planilha.getRow(
                                    planilha.getFirstRowNum()
                            );

                    if (cabecalho == null) {

                        throw new IllegalArgumentException(
                                "O arquivo Excel não possui cabeçalho."
                        );

                    }

                    Map<String, Integer> colunas
                            = mapearColunas(cabecalho);

                    TipoLayout tipoLayout
                            = identificarLayout(colunas);

                    if (tipoLayout == TipoLayout.FINVISTA_PADRAO) {

                        validarColunasObrigatorias(colunas);

                    }

                    int primeiraLinhaDados
                            = cabecalho.getRowNum() + 1;

                    for (int numeroLinha = primeiraLinhaDados;
                            numeroLinha <= planilha.getLastRowNum();
                            numeroLinha++) {

                        Row linha
                                = planilha.getRow(numeroLinha);

                        if (linha == null || linhaVazia(linha)) {

                            continue;

                        }

                        FinancialTransaction lancamento
                                = converterLinha(
                                        linha,
                                        cabecalho,
                                        colunas,
                                        tipoLayout
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

        String nomeArquivo
                = arquivo.getOriginalFilename();

        if (nomeArquivo == null || nomeArquivo.isBlank()) {

            throw new IllegalArgumentException(
                    "O arquivo enviado não possui um nome válido."
            );

        }

        String nomeNormalizado
                = nomeArquivo.toLowerCase(Locale.ROOT);

        if (!nomeNormalizado.endsWith(".xlsx")
                && !nomeNormalizado.endsWith(".xls")) {

            throw new IllegalArgumentException(
                    "Formato não permitido. Envie um arquivo Excel XLS ou XLSX."
            );

        }

    }

    private Map<String, Integer> mapearColunas(
            Row cabecalho
    ) {

        Map<String, Integer> colunas
                = new HashMap<>();

        for (Cell celula : cabecalho) {

            String nome
                    = dataFormatter
                            .formatCellValue(celula)
                            .trim()
                            .toLowerCase(Locale.ROOT);

            if (!nome.isBlank()) {

                colunas.putIfAbsent(
                        nome,
                        celula.getColumnIndex()
                );

            }

        }

        return colunas;

    }

    private TipoLayout identificarLayout(
            Map<String, Integer> colunas
    ) {

        boolean possuiCliente
                = colunas.containsKey("identificador do cliente")
                || colunas.containsKey("nome do cliente");

        boolean possuiFornecedor
                = colunas.containsKey("identificador do fornecedor")
                || colunas.containsKey("nome do fornecedor");

        boolean possuiEstruturaContaAzul
                = colunas.containsKey("data de competência")
                && colunas.containsKey("data de vencimento")
                && colunas.containsKey("descrição")
                && colunas.containsKey(
                        "valor original da parcela (r$)"
                );

        if (possuiEstruturaContaAzul && possuiCliente) {

            return TipoLayout.CONTA_AZUL_RECEBER;

        }

        if (possuiEstruturaContaAzul && possuiFornecedor) {

            return TipoLayout.CONTA_AZUL_PAGAR;

        }

        boolean possuiLayoutFinVista
                = colunas.containsKey("data")
                && colunas.containsKey("descricao")
                && colunas.containsKey("tipo")
                && colunas.containsKey("valor");

        if (possuiLayoutFinVista) {

            return TipoLayout.FINVISTA_PADRAO;

        }

        throw new IllegalArgumentException(
                "Não foi possível reconhecer automaticamente "
                + "a estrutura da planilha."
        );

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
            Row cabecalho,
            Map<String, Integer> colunas,
            TipoLayout tipoLayout
    ) {

        if (tipoLayout == TipoLayout.FINVISTA_PADRAO) {
            return converterLinhaFinVista(
                    linha,
                    colunas
            );
        }

        return converterLinhaContaAzul(
                linha,
                cabecalho,
                colunas,
                tipoLayout
        );
    }

    private FinancialTransaction converterLinhaFinVista(
            Row linha,
            Map<String, Integer> colunas
    ) {

        int numeroLinha
                = linha.getRowNum() + 1;

        LocalDate data
                = converterData(
                        linha,
                        colunas.get("data"),
                        numeroLinha,
                        "data"
                );

        String descricao
                = obterCampoObrigatorio(
                        linha,
                        colunas,
                        "descricao"
                );

        String tipo
                = converterTipo(
                        obterCampoObrigatorio(
                                linha,
                                colunas,
                                "tipo"
                        ),
                        numeroLinha
                );

        BigDecimal valor
                = converterValor(
                        linha,
                        colunas.get("valor"),
                        numeroLinha,
                        "valor"
                );

        String categoria
                = obterCampoOpcional(
                        linha,
                        colunas,
                        "categoria"
                );

        String centroCusto
                = obterCampoOpcional(
                        linha,
                        colunas,
                        "centro_custo"
                );

        String documentoReferencia
                = obterCampoOpcional(
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

    private FinancialTransaction converterLinhaContaAzul(
            Row linha,
            Row cabecalho,
            Map<String, Integer> colunas,
            TipoLayout tipoLayout
    ) {

        int numeroLinha
                = linha.getRowNum() + 1;

        String tipo
                = tipoLayout == TipoLayout.CONTA_AZUL_RECEBER
                        ? "RECEITA"
                        : "DESPESA";

        String colunaIdentificadorEntidade
                = tipoLayout == TipoLayout.CONTA_AZUL_RECEBER
                        ? "identificador do cliente"
                        : "identificador do fornecedor";

        String colunaNomeEntidade
                = tipoLayout == TipoLayout.CONTA_AZUL_RECEBER
                        ? "nome do cliente"
                        : "nome do fornecedor";

        LocalDate dataCompetencia
                = converterData(
                        linha,
                        colunas.get("data de competência"),
                        numeroLinha,
                        "data de competência"
                );

        LocalDate dataVencimento
                = converterDataOpcional(
                        linha,
                        colunas.get("data de vencimento"),
                        numeroLinha,
                        "data de vencimento"
                );

        LocalDate dataPrevista
                = converterDataOpcional(
                        linha,
                        colunas.get("data prevista"),
                        numeroLinha,
                        "data prevista"
                );

        String descricao
                = obterCampoObrigatorio(
                        linha,
                        colunas,
                        "descrição"
                );

        String categoria
                = obterCampoOpcional(
                        linha,
                        colunas,
                        "categoria 1"
                );

        String centroCusto
        = obterCampoOpcional(
                linha,
                colunas,
                "centro de custo 1"
        );        

        BigDecimal valorOriginal
                = converterValor(
                        linha,
                        colunas.get(
                                "valor original da parcela (r$)"
                        ),
                        numeroLinha,
                        "valor original da parcela (r$)"
                );

        BigDecimal valorRealizado
                = converterValorOpcional(
                        linha,
                        localizarPrimeiraColuna(
                                colunas,
                                "valor recebido da parcela (r$)",
                                "valor pago da parcela (r$)"
                        ),
                        numeroLinha,
                        "valor realizado"
                );

        BigDecimal valorAberto
                = converterValorOpcional(
                        linha,
                        localizarPrimeiraColuna(
                                colunas,
                                "valor em aberto da parcela (r$)",
                                "valor aberto da parcela (r$)"
                        ),
                        numeroLinha,
                        "valor em aberto"
                );

        String codigoReferencia
                = obterCampoOpcional(
                        linha,
                        colunas,
                        "código de referência"
                );

        String situacao
                = obterCampoOpcional(
                        linha,
                        colunas,
                        "situação"
                );

        String recorrencia
                = obterCampoOpcional(
                        linha,
                        colunas,
                        "recorrência"
                );

        String quantidadeRecorrencia
                = obterCampoOpcional(
                        linha,
                        colunas,
                        "quantidade de recorrência"
                );

        String entidadeExternaId
                = obterCampoOpcional(
                        linha,
                        colunas,
                        colunaIdentificadorEntidade
                );

        String entidadeNome
                = obterCampoOpcional(
                        linha,
                        colunas,
                        colunaNomeEntidade
                );

        String formaMovimentacao
                = obterPrimeiroCampoOpcional(
                        linha,
                        colunas,
                        "forma de recebimento",
                        "forma de pagamento"
                );

        String contaBancaria
                = obterPrimeiroCampoOpcional(
                        linha,
                        colunas,
                        "conta bancária",
                        "conta"
                );

        String notaFiscal
                = obterPrimeiroCampoOpcional(
                        linha,
                        colunas,
                        "nota fiscal",
                        "número da nota fiscal"
                );

        String observacoes
                = obterPrimeiroCampoOpcional(
                        linha,
                        colunas,
                        "observações",
                        "observação"
                );

        Boolean agendado
                = converterBooleanoOpcional(
                        obterCampoOpcional(
                                linha,
                                colunas,
                                "agendado"
                        )
                );

        String documentoReferencia
                = codigoReferencia != null
                        ? codigoReferencia
                        : entidadeExternaId;

        FinancialTransaction lancamento
                = new FinancialTransaction(
                        dataCompetencia,
                        descricao,
                        tipo,
                        valorOriginal,
                        categoria,
                        centroCusto,
                        "CONTA_AZUL_EXCEL",
                        documentoReferencia
                );

        lancamento.setDataCompetencia(
                dataCompetencia
        );

        lancamento.setDataVencimento(
                dataVencimento
        );

        lancamento.setDataPrevista(
                dataPrevista
        );

        lancamento.setValorOriginal(
                valorOriginal
        );

        lancamento.setValorRealizado(
                valorRealizado
        );

        lancamento.setValorAberto(
                valorAberto
        );

        lancamento.setEntidadeExternaId(
                entidadeExternaId
        );

        lancamento.setEntidadeNome(
                entidadeNome
        );

        lancamento.setCodigoReferencia(
                codigoReferencia
        );

        lancamento.setSituacao(
                situacao
        );

        lancamento.setRecorrencia(
                recorrencia
        );

        lancamento.setQuantidadeRecorrencia(
                quantidadeRecorrencia
        );

        lancamento.setAgendado(
                agendado
        );

        lancamento.setFormaMovimentacao(
                formaMovimentacao
        );

        lancamento.setContaBancaria(
                contaBancaria
        );

        lancamento.setNotaFiscal(
                notaFiscal
        );

        lancamento.setObservacoes(
                observacoes
        );

        return lancamento;

    }

    private Integer localizarPrimeiraColuna(
            Map<String, Integer> colunas,
            String... nomes
    ) {

        for (String nome : nomes) {

            Integer indice
                    = colunas.get(nome);

            if (indice != null) {

                return indice;

            }

        }

        return null;

    }

    private String obterPrimeiroCampoOpcional(
            Row linha,
            Map<String, Integer> colunas,
            String... nomes
    ) {

        for (String nome : nomes) {

            String valor
                    = obterCampoOpcional(
                            linha,
                            colunas,
                            nome
                    );

            if (valor != null && !valor.isBlank()) {

                return valor;

            }

        }

        return null;

    }

    private String obterCampoObrigatorio(
            Row linha,
            Map<String, Integer> colunas,
            String coluna
    ) {

        Integer indice
                = colunas.get(coluna);

        if (indice == null) {

            throw new IllegalArgumentException(
                    "Coluna obrigatória não encontrada: "
                    + coluna
            );

        }

        String valor
                = obterTexto(
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

        Integer indice
                = colunas.get(coluna);

        if (indice == null) {

            return null;

        }

        String valor
                = obterTexto(
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
            int numeroLinha,
            String nomeCampo
    ) {

        if (indice == null) {

            throw new IllegalArgumentException(
                    "Coluna obrigatória não encontrada: "
                    + nomeCampo
            );

        }

        Cell celula
                = linha.getCell(
                        indice,
                        Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
                );

        if (celula == null) {

            throw new IllegalArgumentException(
                    "Campo obrigatório vazio na linha "
                    + numeroLinha
                    + ": "
                    + nomeCampo
            );

        }

        LocalDate data
                = converterCelulaData(celula);

        if (data == null) {

            throw new IllegalArgumentException(
                    "Data inválida na linha "
                    + numeroLinha
                    + ": "
                    + obterTexto(celula)
                    + " ("
                    + nomeCampo
                    + ")"
            );

        }

        return data;

    }

    private LocalDate converterDataOpcional(
            Row linha,
            Integer indice,
            int numeroLinha,
            String nomeCampo
    ) {

        if (indice == null) {

            return null;

        }

        Cell celula
                = linha.getCell(
                        indice,
                        Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
                );

        if (celula == null) {

            return null;

        }

        String texto
                = obterTexto(celula);

        if (texto == null
                || texto.isBlank()
                || texto.equals("-")) {

            return null;

        }

        LocalDate data
                = converterCelulaData(celula);

        if (data == null) {

            throw new IllegalArgumentException(
                    "Data inválida na linha "
                    + numeroLinha
                    + ": "
                    + texto
                    + " ("
                    + nomeCampo
                    + ")"
            );

        }

        return data;

    }

    private LocalDate converterCelulaData(
            Cell celula
    ) {

        if (celula.getCellType() == CellType.NUMERIC
                && DateUtil.isCellDateFormatted(celula)) {

            return celula
                    .getDateCellValue()
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();

        }

        String valor
                = obterTexto(celula);

        if (valor == null
                || valor.isBlank()
                || valor.equals("-")) {

            return null;

        }

        try {

            return LocalDate.parse(
                    valor,
                    DATE_FORMATTER
            );

        } catch (DateTimeParseException exception) {

            return null;

        }

    }

    private String converterTipo(
            String valor,
            int numeroLinha
    ) {

        String tipo
                = valor.trim()
                        .toUpperCase(Locale.ROOT);

        if (!tipo.equals("RECEITA")
                && !tipo.equals("DESPESA")) {

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
            int numeroLinha,
            String nomeCampo
    ) {

        if (indice == null) {

            throw new IllegalArgumentException(
                    "Coluna obrigatória não encontrada: "
                    + nomeCampo
            );

        }

        Cell celula
                = linha.getCell(
                        indice,
                        Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
                );

        if (celula == null) {

            throw new IllegalArgumentException(
                    "Campo obrigatório vazio na linha "
                    + numeroLinha
                    + ": "
                    + nomeCampo
            );

        }

        BigDecimal valor
                = converterCelulaValor(
                        celula,
                        numeroLinha,
                        nomeCampo
                );

        if (valor == null) {

            throw new IllegalArgumentException(
                    "Campo obrigatório vazio na linha "
                    + numeroLinha
                    + ": "
                    + nomeCampo
            );

        }

        return valor;

    }

    private BigDecimal converterValorOpcional(
            Row linha,
            Integer indice,
            int numeroLinha,
            String nomeCampo
    ) {

        if (indice == null) {

            return null;

        }

        Cell celula
                = linha.getCell(
                        indice,
                        Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
                );

        if (celula == null) {

            return null;

        }

        String texto
                = obterTexto(celula);

        if (texto == null
                || texto.isBlank()
                || texto.equals("-")) {

            return null;

        }

        return converterCelulaValor(
                celula,
                numeroLinha,
                nomeCampo
        );

    }

    private BigDecimal converterCelulaValor(
            Cell celula,
            int numeroLinha,
            String nomeCampo
    ) {

        if (celula.getCellType() == CellType.NUMERIC) {

            BigDecimal valor
                    = BigDecimal.valueOf(
                            celula.getNumericCellValue()
                    );

            validarValorNaoNegativo(
                    valor,
                    numeroLinha,
                    nomeCampo
            );

            return valor;

        }

        String texto
                = obterTexto(celula);

        if (texto == null
                || texto.isBlank()
                || texto.equals("-")) {

            return null;

        }

        try {

            String valorNormalizado
                    = texto
                            .replace("R$", "")
                            .replace("\u00A0", "")
                            .replace(" ", "")
                            .trim();

            if (valorNormalizado.contains(".")
                    && valorNormalizado.contains(",")) {

                valorNormalizado
                        = valorNormalizado
                                .replace(".", "")
                                .replace(",", ".");

            } else if (valorNormalizado.contains(",")) {

                valorNormalizado
                        = valorNormalizado
                                .replace(",", ".");

            }

            BigDecimal valor
                    = new BigDecimal(
                            valorNormalizado
                    );

            validarValorNaoNegativo(
                    valor,
                    numeroLinha,
                    nomeCampo
            );

            return valor;

        } catch (NumberFormatException exception) {

            throw new IllegalArgumentException(
                    "Valor financeiro inválido na linha "
                    + numeroLinha
                    + ": "
                    + texto
                    + " ("
                    + nomeCampo
                    + ")"
            );

        }

    }

    private void validarValorNaoNegativo(
            BigDecimal valor,
            int numeroLinha,
            String nomeCampo
    ) {

        if (valor.compareTo(
                BigDecimal.ZERO
        ) < 0) {

            throw new IllegalArgumentException(
                    "O valor não pode ser negativo na linha "
                    + numeroLinha
                    + ": "
                    + nomeCampo
            );

        }

    }

    private Boolean converterBooleanoOpcional(
            String valor
    ) {

        if (valor == null
                || valor.isBlank()
                || valor.equals("-")) {

            return null;

        }

        String normalizado
                = valor.trim()
                        .toLowerCase(Locale.ROOT);

        if (normalizado.equals("sim")
                || normalizado.equals("s")
                || normalizado.equals("true")
                || normalizado.equals("1")) {

            return true;

        }

        if (normalizado.equals("não")
                || normalizado.equals("nao")
                || normalizado.equals("n")
                || normalizado.equals("false")
                || normalizado.equals("0")) {

            return false;

        }

        return null;

    }

    private boolean linhaVazia(
            Row linha
    ) {

        for (int indice = linha.getFirstCellNum();
                indice < linha.getLastCellNum();
                indice++) {

            if (indice < 0) {

                continue;

            }

            Cell celula
                    = linha.getCell(
                            indice,
                            Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
                    );

            String valor
                    = obterTexto(celula);

            if (valor != null && !valor.isBlank()) {

                return false;

            }

        }

        return true;

    }

}
