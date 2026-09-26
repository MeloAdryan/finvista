package com.finvista.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

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
                        SecurityContextRepository securityContextRepository)
                        throws Exception {

                http
                                .cors(cors -> {
                                })

                                .csrf(csrf -> csrf.disable())

                                .securityContext(securityContext -> securityContext
                                                .securityContextRepository(
                                                                securityContextRepository))

                                .formLogin(form -> form.disable())
                                .httpBasic(basic -> basic.disable())

                                .exceptionHandling(exception -> exception
                                                .authenticationEntryPoint(
                                                                (request, response, authException) -> response
                                                                                .sendError(
                                                                                                HttpServletResponse.SC_UNAUTHORIZED))
                                                .accessDeniedHandler(
                                                                (request, response, accessDeniedException) -> response
                                                                                .sendError(
                                                                                                HttpServletResponse.SC_FORBIDDEN)))

                                .authorizeHttpRequests(auth -> auth

                                                .requestMatchers(
                                                                "/api/auth/login")
                                                .permitAll()

                                                .requestMatchers(
                                                                "/api/importacoes/**")
                                                .hasRole("ADMIN")

                                                .anyRequest().authenticated());

                return http.build();
        }
}