package com.finvista.service;

import com.finvista.model.Usuario;
import com.finvista.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Usuario criarUsuario(
            String nome,
            String email,
            String senha,
            String perfil
    ) {
        validarTexto(nome, "Nome");
        validarTexto(email, "E-mail");
        validarTexto(senha, "Senha");
        validarTexto(perfil, "Perfil");

        String emailNormalizado = email
                .trim()
                .toLowerCase(Locale.ROOT);

        if (usuarioRepository.existsByEmailIgnoreCase(emailNormalizado)) {
            throw new IllegalArgumentException(
                    "Já existe um usuário cadastrado com este e-mail."
            );
        }

        if (senha.length() < 8) {
            throw new IllegalArgumentException(
                    "A senha deve possuir pelo menos 8 caracteres."
            );
        }

        String senhaHash = passwordEncoder.encode(senha);

        Usuario usuario = new Usuario(
                nome.trim(),
                emailNormalizado,
                senhaHash,
                perfil.trim().toUpperCase(Locale.ROOT)
        );

        return usuarioRepository.save(usuario);
    }

    public Optional<Usuario> buscarPorEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }

        return usuarioRepository.findByEmailIgnoreCase(
                email.trim().toLowerCase(Locale.ROOT)
        );
    }

    public boolean verificarSenha(
            Usuario usuario,
            String senha
    ) {
        if (usuario == null || senha == null) {
            return false;
        }

        return passwordEncoder.matches(
                senha,
                usuario.getSenhaHash()
        );
    }

    private void validarTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    campo + " é obrigatório."
            );
        }
    }
}