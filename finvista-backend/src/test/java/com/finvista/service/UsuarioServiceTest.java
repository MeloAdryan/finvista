package com.finvista.service;

import com.finvista.model.Usuario;
import com.finvista.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UsuarioServiceTest {

    private UsuarioRepository usuarioRepository;
    private PasswordEncoder passwordEncoder;
    private UsuarioService usuarioService;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);

        passwordEncoder = new BCryptPasswordEncoder();

        usuarioService = new UsuarioService(
                usuarioRepository,
                passwordEncoder
        );
    }

    @Test
    void deveCriarUsuarioComSenhaCriptografada() {
        when(usuarioRepository.existsByEmailIgnoreCase("admin@finvista.com"))
                .thenReturn(false);

        when(usuarioRepository.save(any(Usuario.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Usuario usuario = usuarioService.criarUsuario(
                "Administrador FinVista",
                "ADMIN@FINVISTA.COM",
                "FinVista@123",
                "admin"
        );

        assertNotNull(usuario);

        assertEquals(
                "Administrador FinVista",
                usuario.getNome()
        );

        assertEquals(
                "admin@finvista.com",
                usuario.getEmail()
        );

        assertEquals(
                "ADMIN",
                usuario.getPerfil()
        );

        assertTrue(usuario.getAtivo());

        assertNotNull(usuario.getSenhaHash());

        assertNotEquals(
                "FinVista@123",
                usuario.getSenhaHash()
        );

        assertTrue(
                passwordEncoder.matches(
                        "FinVista@123",
                        usuario.getSenhaHash()
                )
        );

        verify(usuarioRepository).save(any(Usuario.class));
    }

    @Test
    void deveVerificarSenhaCorreta() {
        String hash = passwordEncoder.encode("FinVista@123");

        Usuario usuario = new Usuario(
                "Administrador",
                "admin@finvista.com",
                hash,
                "ADMIN"
        );

        boolean resultado = usuarioService.verificarSenha(
                usuario,
                "FinVista@123"
        );

        assertTrue(resultado);
    }

    @Test
    void deveRejeitarSenhaIncorreta() {
        String hash = passwordEncoder.encode("FinVista@123");

        Usuario usuario = new Usuario(
                "Administrador",
                "admin@finvista.com",
                hash,
                "ADMIN"
        );

        boolean resultado = usuarioService.verificarSenha(
                usuario,
                "SenhaErrada123"
        );

        assertFalse(resultado);
    }

    @Test
    void deveRejeitarEmailDuplicado() {
        when(usuarioRepository.existsByEmailIgnoreCase("admin@finvista.com"))
                .thenReturn(true);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> usuarioService.criarUsuario(
                                "Outro Administrador",
                                "admin@finvista.com",
                                "OutraSenha123",
                                "ADMIN"
                        )
                );

        assertEquals(
                "Já existe um usuário cadastrado com este e-mail.",
                exception.getMessage()
        );

        verify(usuarioRepository, never())
                .save(any(Usuario.class));
    }

    @Test
    void deveRejeitarSenhaComMenosDeOitoCaracteres() {
        when(usuarioRepository.existsByEmailIgnoreCase("admin@finvista.com"))
                .thenReturn(false);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> usuarioService.criarUsuario(
                                "Administrador",
                                "admin@finvista.com",
                                "1234567",
                                "ADMIN"
                        )
                );

        assertEquals(
                "A senha deve possuir pelo menos 8 caracteres.",
                exception.getMessage()
        );

        verify(usuarioRepository, never())
                .save(any(Usuario.class));
    }

    @Test
    void deveBuscarUsuarioPorEmailIgnorandoMaiusculas() {
        Usuario usuario = new Usuario(
                "Administrador",
                "admin@finvista.com",
                passwordEncoder.encode("FinVista@123"),
                "ADMIN"
        );

        when(usuarioRepository.findByEmailIgnoreCase("admin@finvista.com"))
                .thenReturn(Optional.of(usuario));

        Optional<Usuario> resultado =
                usuarioService.buscarPorEmail(
                        " ADMIN@FINVISTA.COM "
                );

        assertTrue(resultado.isPresent());

        assertEquals(
                "admin@finvista.com",
                resultado.get().getEmail()
        );

        verify(usuarioRepository)
                .findByEmailIgnoreCase("admin@finvista.com");
    }
}