package com.finvista.controller;

import com.finvista.dto.auth.LoginRequest;
import com.finvista.dto.auth.LoginResponse;
import com.finvista.model.Usuario;
import com.finvista.service.AuthService;
import com.finvista.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

        private final AuthService authService;
        private final UsuarioService usuarioService;

        public AuthController(
                        AuthService authService,
                        UsuarioService usuarioService) {
                this.authService = authService;
                this.usuarioService = usuarioService;
        }

        @PostMapping("/login")
        public ResponseEntity<LoginResponse> login(
                        @RequestBody LoginRequest request,
                        HttpServletRequest httpRequest) {

                LoginResponse response = authService.login(request);

                Authentication authentication = new UsernamePasswordAuthenticationToken(
                                response.email(),
                                null,
                                List.of(
                                                new SimpleGrantedAuthority(
                                                                "ROLE_" + response.perfil())));

                SecurityContext securityContext = SecurityContextHolder.createEmptyContext();

                securityContext.setAuthentication(authentication);
                SecurityContextHolder.setContext(securityContext);

                HttpSession session = httpRequest.getSession(true);

                session.setAttribute(
                                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                                securityContext);

                return ResponseEntity.ok(response);
        }

        @GetMapping("/me")
        public ResponseEntity<LoginResponse> me(
                        Authentication authentication) {

                if (authentication == null
                                || !authentication.isAuthenticated()) {
                        return ResponseEntity.status(401).build();
                }

                Usuario usuario = usuarioService
                                .buscarPorEmail(authentication.getName())
                                .orElse(null);

                if (usuario == null
                                || !Boolean.TRUE.equals(usuario.getAtivo())) {
                        return ResponseEntity.status(401).build();
                }

                LoginResponse response = new LoginResponse(
                                usuario.getId(),
                                usuario.getNome(),
                                usuario.getEmail(),
                                usuario.getPerfil());

                return ResponseEntity.ok(response);
        }

        @PostMapping("/logout")
        public ResponseEntity<Void> logout(
                        HttpServletRequest request) {

                HttpSession session = request.getSession(false);

                if (session != null) {
                        session.invalidate();
                }

                SecurityContextHolder.clearContext();

                return ResponseEntity.noContent().build();
        }
}