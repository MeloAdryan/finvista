package com.finvista.config;

import com.finvista.service.UsuarioService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminInitializer implements CommandLineRunner {

    private final UsuarioService usuarioService;

    @Value("${finvista.admin.nome:}")
    private String nome;

    @Value("${finvista.admin.email:}")
    private String email;

    @Value("${finvista.admin.senha:}")
    private String senha;

    public AdminInitializer(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Override
    public void run(String... args) {

        if (nome == null || nome.isBlank()
                || email == null || email.isBlank()
                || senha == null || senha.isBlank()) {
            return;
        }

        if (usuarioService.buscarPorEmail(email).isPresent()) {
            return;
        }

        usuarioService.criarUsuario(
                nome,
                email,
                senha,
                "ADMIN"
        );

        System.out.println(
                "Administrador inicial do FinVista criado com sucesso."
        );
    }
}