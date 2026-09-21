package com.finvista.service;

import com.finvista.model.FinancialTransaction;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class FinancialTransactionValidationService {

    private static final int TAMANHO_MAXIMO_DESCRICAO = 255;

    private static final int TAMANHO_MAXIMO_DOCUMENTO_REFERENCIA = 255;

    private static final LocalDate DATA_MINIMA_PERMITIDA =
            LocalDate.of(2000, 1, 1);


    public String validarESanearDescricao(
            String descricao,
            int numeroLinha
    ) {

        if (descricao == null || descricao.isBlank()) {
            throw new IllegalArgumentException(
                    "Descrição obrigatória vazia na linha "
                            + numeroLinha
                            + "."
            );
        }

        String descricaoSaneada =
                descricao
                        .trim()
                        .replaceAll("\\s+", " ");

        if (descricaoSaneada.length()
                > TAMANHO_MAXIMO_DESCRICAO) {

            throw new IllegalArgumentException(
                    "Descrição excede o limite de "
                            + TAMANHO_MAXIMO_DESCRICAO
                            + " caracteres na linha "
                            + numeroLinha
                            + "."
            );
        }

        return descricaoSaneada;
    }


    public BigDecimal validarValor(
            BigDecimal valor,
            int numeroLinha
    ) {

        if (valor == null) {
            throw new IllegalArgumentException(
                    "Valor obrigatório não informado na linha "
                            + numeroLinha
                            + "."
            );
        }

        if (valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "O valor deve ser maior que zero na linha "
                            + numeroLinha
                            + ": "
                            + valor
            );
        }

        return valor;
    }


    public LocalDate validarData(
            LocalDate data,
            int numeroLinha
    ) {

        if (data == null) {
            throw new IllegalArgumentException(
                    "Data obrigatória não informada na linha "
                            + numeroLinha
                            + "."
            );
        }

        if (data.isBefore(DATA_MINIMA_PERMITIDA)) {
            throw new IllegalArgumentException(
                    "Data anterior ao limite permitido na linha "
                            + numeroLinha
                            + ": "
                            + data
                            + ". A data mínima permitida é "
                            + DATA_MINIMA_PERMITIDA
                            + "."
            );
        }

        return data;
    }


    public String validarESanearDocumentoReferencia(
            String documentoReferencia,
            int numeroLinha
    ) {

        if (documentoReferencia == null
                || documentoReferencia.isBlank()) {
            return null;
        }

        String documentoSaneado =
                documentoReferencia
                        .trim()
                        .replaceAll("\\s+", " ");

        if (documentoSaneado.length()
                > TAMANHO_MAXIMO_DOCUMENTO_REFERENCIA) {

            throw new IllegalArgumentException(
                    "Documento de referência excede o limite de "
                            + TAMANHO_MAXIMO_DOCUMENTO_REFERENCIA
                            + " caracteres na linha "
                            + numeroLinha
                            + "."
            );
        }

        return documentoSaneado;
    }


    public void validarLancamento(
            FinancialTransaction lancamento,
            int numeroLinha
    ) {

        if (lancamento == null) {
            throw new IllegalArgumentException(
                    "Lançamento financeiro inválido na linha "
                            + numeroLinha
                            + "."
            );
        }

        validarData(
                lancamento.getData(),
                numeroLinha
        );

        lancamento.setDescricao(
                validarESanearDescricao(
                        lancamento.getDescricao(),
                        numeroLinha
                )
        );

        validarValor(
                lancamento.getValor(),
                numeroLinha
        );

        if (lancamento.getTipo() == null
                || lancamento.getTipo().isBlank()) {

            throw new IllegalArgumentException(
                    "Tipo financeiro obrigatório não informado na linha "
                            + numeroLinha
                            + "."
            );
        }

        if (!"RECEITA".equals(lancamento.getTipo())
                && !"DESPESA".equals(lancamento.getTipo())) {

            throw new IllegalArgumentException(
                    "Tipo financeiro inválido na linha "
                            + numeroLinha
                            + ": "
                            + lancamento.getTipo()
                            + "."
            );
        }

        lancamento.setDocumentoReferencia(
                validarESanearDocumentoReferencia(
                        lancamento.getDocumentoReferencia(),
                        numeroLinha
                )
        );

        if (lancamento.getOrigem() == null
                || lancamento.getOrigem().isBlank()) {

            throw new IllegalArgumentException(
                    "Origem obrigatória não informada na linha "
                            + numeroLinha
                            + "."
            );
        }
    }
}