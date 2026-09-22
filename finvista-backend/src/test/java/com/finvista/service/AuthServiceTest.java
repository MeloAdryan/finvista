package com.finvista.service;

import com.finvista.dto.auth.LoginRequest;
import com.finvista.dto.auth.LoginResponse;
import com.finvista.model.Usuario;
import com.finvista.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private UsuarioRepository usuarioRepository;
    private UsuarioService usuarioService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);

        usuarioService = new UsuarioService(
                usuarioRepository,
                new BCryptPasswordEncoder()
        );

        authService = new AuthService(usuarioService);
    }

    @Test
    void deveRealizarLoginComCredenciaisValidas() {
        String senha = "senha-segura";

        Usuario usuario = new Usuario(
                "Administrador FinVista",
                "admin@finvista.com",
                new BCryptPasswordEncoder().encode(senha),
                "ADMIN"
        );

        when(
                usuarioRepository.findByEmailIgnoreCase(
                        "admin@finvista.com"
                )
        ).thenReturn(Optional.of(usuario));

        LoginResponse response = authService.login(
                new LoginRequest(
                        "admin@finvista.com",
                        senha
                )
        );

        assertEquals(
                "Administrador FinVista",
                response.nome()
        );

        assertEquals(
                "admin@finvista.com",
                response.email()
        );

        assertEquals(
                "ADMIN",
                response.perfil()
        );
    }

    @Test
    void deveRecusarEmailInexistente() {
        when(
                usuarioRepository.findByEmailIgnoreCase(
                        "inexistente@finvista.com"
                )
        ).thenReturn(Optional.empty());

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> authService.login(
                                new LoginRequest(
                                        "inexistente@finvista.com",
                                        "senha-segura"
                                )
                        )
                );

        assertEquals(
                "E-mail ou senha inválidos.",
                exception.getMessage()
        );
    }

    @Test
    void deveRecusarSenhaIncorreta() {
        Usuario usuario = new Usuario(
                "Cliente FinVista",
                "cliente@finvista.com",
                new BCryptPasswordEncoder()
                        .encode("senha-correta"),
                "CLIENTE"
        );

        when(
                usuarioRepository.findByEmailIgnoreCase(
                        "cliente@finvista.com"
                )
        ).thenReturn(Optional.of(usuario));

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> authService.login(
                                new LoginRequest(
                                        "cliente@finvista.com",
                                        "senha-incorreta"
                                )
                        )
                );

        assertEquals(
                "E-mail ou senha inválidos.",
                exception.getMessage()
        );
    }

    @Test
    void deveRecusarUsuarioInativo() {
        Usuario usuario = new Usuario(
                "Cliente Inativo",
                "inativo@finvista.com",
                new BCryptPasswordEncoder()
                        .encode("senha-segura"),
                "CLIENTE"
        );

        usuario.setAtivo(false);

        when(
                usuarioRepository.findByEmailIgnoreCase(
                        "inativo@finvista.com"
                )
        ).thenReturn(Optional.of(usuario));

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> authService.login(
                                new LoginRequest(
                                        "inativo@finvista.com",
                                        "senha-segura"
                                )
                        )
                );

        assertEquals(
                "Usuário inativo.",
                exception.getMessage()
        );
    }

    @Test
    void deveRecusarEmailVazio() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> authService.login(
                                new LoginRequest(
                                        "",
                                        "senha-segura"
                                )
                        )
                );

        assertEquals(
                "E-mail é obrigatório.",
                exception.getMessage()
        );

        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void deveRecusarSenhaVazia() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> authService.login(
                                new LoginRequest(
                                        "admin@finvista.com",
                                        ""
                                )
                        )
                );

        assertEquals(
                "Senha é obrigatória.",
                exception.getMessage()
        );

        verifyNoInteractions(usuarioRepository);
    }
}