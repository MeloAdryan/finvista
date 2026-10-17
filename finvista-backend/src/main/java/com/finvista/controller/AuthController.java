package com.finvista.controller;

import com.finvista.dto.auth.LoginRequest;
import com.finvista.dto.auth.LoginResponse;
import com.finvista.model.Usuario;
import com.finvista.service.AuthService;
import com.finvista.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UsuarioService usuarioService;

    public AuthController(AuthService authService, UsuarioService usuarioService) {
        this.authService = authService;
        this.usuarioService = usuarioService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        LoginResponse response = authService.login(request);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                response.email(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + response.perfil())));
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);

        HttpSession anterior = httpRequest.getSession(false);
        if (anterior != null) {
            anterior.invalidate();
        }
        HttpSession nova = httpRequest.getSession(true);
        nova.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                securityContext);
        SecurityContextHolder.setContext(securityContext);

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<LoginResponse> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).cacheControl(CacheControl.noStore()).build();
        }
        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName()).orElse(null);
        if (usuario == null || !Boolean.TRUE.equals(usuario.getAtivo())) {
            return ResponseEntity.status(401).cacheControl(CacheControl.noStore()).build();
        }
        LoginResponse response = new LoginResponse(usuario.getId(), usuario.getNome(),
                usuario.getEmail(), usuario.getPerfil());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
