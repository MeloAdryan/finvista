package com.finvista.exception;

import com.finvista.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    @Test
    void deveRetornarBadRequestParaIllegalArgumentException() {

        GlobalExceptionHandler handler =
                new GlobalExceptionHandler();

        HttpServletRequest request =
                mock(HttpServletRequest.class);

        when(request.getRequestURI())
                .thenReturn("/api/teste");

        IllegalArgumentException exception =
                new IllegalArgumentException(
                        "Valor informado é inválido"
                );

        ResponseEntity<ApiErrorResponse> resposta =
                handler.tratarArgumentoInvalido(
                        exception,
                        request
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                resposta.getStatusCode()
        );

        assertNotNull(resposta.getBody());

        assertEquals(
                400,
                resposta.getBody().status()
        );

        assertEquals(
                "Bad Request",
                resposta.getBody().erro()
        );

        assertEquals(
                "Valor informado é inválido",
                resposta.getBody().mensagem()
        );

        assertEquals(
                "/api/teste",
                resposta.getBody().caminho()
        );

        assertNotNull(
                resposta.getBody().timestamp()
        );
    }
}