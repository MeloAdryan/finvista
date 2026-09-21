package com.finvista.service;

import com.finvista.model.FinancialTransaction;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class CsvImportService {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public List<FinancialTransaction> processar(
            MultipartFile arquivo
    ) throws IOException {

        validarArquivo(arquivo);

        String conteudo =
                new String(
                        arquivo.getBytes(),
                        StandardCharsets.UTF_8
                );

        conteudo = removerBom(conteudo);

        char delimitador =
                detectarDelimitador(conteudo);

        List<FinancialTransaction> lancamentos =
                new ArrayList<>();

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
                                .setIgnoreHeaderCase(true)
                                .setTrim(true)
                                .get()
                                .parse(reader)
        ) {

            for (CSVRecord registro : parser) {

                if (registroVazio(registro)) {
                    continue;
                }

                FinancialTransaction lancamento =
                        converterRegistro(registro);

                lancamentos.add(lancamento);
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

    private FinancialTransaction converterRegistro(
            CSVRecord registro
    ) {

        LocalDate data =
                converterData(
                        obterCampoObrigatorio(
                                registro,
                                "data"
                        )
                );

        String descricao =
                obterCampoObrigatorio(
                        registro,
                        "descricao"
                );

        String tipo =
                converterTipo(
                        obterCampoObrigatorio(
                                registro,
                                "tipo"
                        )
                );

        BigDecimal valor =
                converterValor(
                        obterCampoObrigatorio(
                                registro,
                                "valor"
                        )
                );

        String categoria =
                obterCampoOpcional(
                        registro,
                        "categoria"
                );

        String centroCusto =
                obterCampoOpcional(
                        registro,
                        "centro_custo"
                );

        String documentoReferencia =
                obterCampoOpcional(
                        registro,
                        "documento_referencia"
                );

        return new FinancialTransaction(
                data,
                descricao,
                tipo,
                valor,
                categoria,
                centroCusto,
                "CSV",
                documentoReferencia
        );
    }

    private void validarArquivo(
            MultipartFile arquivo
    ) {

        if (arquivo == null || arquivo.isEmpty()) {
            throw new IllegalArgumentException(
                    "O arquivo CSV está vazio."
            );
        }

        String nomeArquivo =
                arquivo.getOriginalFilename();

        if (
                nomeArquivo == null
                        || !nomeArquivo
                        .toLowerCase(Locale.ROOT)
                        .endsWith(".csv")
        ) {
            throw new IllegalArgumentException(
                    "O arquivo deve estar no formato CSV."
            );
        }
    }

    private String obterCampoObrigatorio(
            CSVRecord registro,
            String coluna
    ) {

        if (!registro.isMapped(coluna)) {
            throw new IllegalArgumentException(
                    "Coluna obrigatória não encontrada: "
                            + coluna
            );
        }

        String valor =
                registro.get(coluna);

        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    "Campo obrigatório vazio na linha "
                            + registro.getRecordNumber()
                            + ": "
                            + coluna
            );
        }

        return valor.trim();
    }

    private String obterCampoOpcional(
            CSVRecord registro,
            String coluna
    ) {

        if (!registro.isMapped(coluna)) {
            return null;
        }

        String valor =
                registro.get(coluna);

        if (valor == null || valor.isBlank()) {
            return null;
        }

        return valor.trim();
    }

    private LocalDate converterData(
            String valor
    ) {

        try {

            return LocalDate.parse(
                    valor,
                    DATE_FORMATTER
            );

        } catch (DateTimeParseException exception) {

            throw new IllegalArgumentException(
                    "Data inválida: "
                            + valor
                            + ". Use o formato dd/MM/yyyy."
            );
        }
    }

    private String converterTipo(
            String valor
    ) {

        String tipo =
                valor.trim()
                        .toUpperCase(Locale.ROOT);

        if (
                !tipo.equals("RECEITA")
                        && !tipo.equals("DESPESA")
        ) {
            throw new IllegalArgumentException(
                    "Tipo inválido: "
                            + valor
                            + ". Use RECEITA ou DESPESA."
            );
        }

        return tipo;
    }

    private BigDecimal converterValor(
            String valor
    ) {

        try {

            String valorNormalizado =
                    valor
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

            BigDecimal resultado =
                    new BigDecimal(
                            valorNormalizado
                    );

            if (
                    resultado.compareTo(
                            BigDecimal.ZERO
                    ) < 0
            ) {
                throw new IllegalArgumentException(
                        "O valor não pode ser negativo: "
                                + valor
                );
            }

            return resultado;

        } catch (NumberFormatException exception) {

            throw new IllegalArgumentException(
                    "Valor financeiro inválido: "
                            + valor
            );
        }
    }
}