package com.finvista.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository
    ) throws Exception {

        http
                .cors(cors -> {
                })
                .csrf(csrf -> csrf.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
                .securityContext(securityContext
                        -> securityContext.securityContextRepository(
                                securityContextRepository
                        )
                )
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .exceptionHandling(exception
                        -> exception
                                .authenticationEntryPoint(
                                        (request,
                                                response,
                                                authException)
                                        -> response.sendError(
                                                HttpServletResponse.SC_UNAUTHORIZED
                                        )
                                )
                                .accessDeniedHandler(
                                        (request,
                                                response,
                                                accessDeniedException)
                                        -> {
                                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                    response.setContentType("application/json");
                                    response.setCharacterEncoding("UTF-8");
                                    if (accessDeniedException instanceof CsrfException) {
                                        response.getWriter().write("{\"codigo\":\"CSRF_INVALID\",\"mensagem\":\"Sessão de segurança inválida ou expirada. Atualize a página e confira a sessão antes de tentar novamente.\"}");
                                    } else {
                                        response.getWriter().write("{\"codigo\":\"ACESSO_NEGADO\",\"mensagem\":\"Seu perfil não tem acesso a esta operação.\"}");
                                    }
                                }
                                )
                )
                .authorizeHttpRequests(auth
                        -> auth
                                /*
                                 * Login público.
                                 */
                                .requestMatchers(
                                        "/api/auth/login", "/api/auth/csrf"
                                )
                                .permitAll()
                                .requestMatchers("/api/admin/**", "/api/importacoes/**", "/api/integracoes/conta-azul/**").hasRole("ADMIN")
                                .anyRequest()
                                .authenticated()
                );

        return http.build();
    }
}
