package com.lootfast.backend.config;

import com.lootfast.backend.security.ApiSecurityErrorHandler;
import com.lootfast.backend.security.JwtRoleConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtRoleConverter jwtRoleConverter;
    private final ApiSecurityErrorHandler apiSecurityErrorHandler;

    public SecurityConfig(
            JwtRoleConverter jwtRoleConverter,
            ApiSecurityErrorHandler apiSecurityErrorHandler
    ) {
        this.jwtRoleConverter = jwtRoleConverter;
        this.apiSecurityErrorHandler = apiSecurityErrorHandler;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/v1/auth/signup",
                                "/api/v1/auth/signin",
                                "/api/v1/auth/verify-otp",
                                "/api/v1/auth/resend-otp",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(apiSecurityErrorHandler)
                        .accessDeniedHandler(apiSecurityErrorHandler)
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(apiSecurityErrorHandler)
                        .accessDeniedHandler(apiSecurityErrorHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtRoleConverter))
                )
                .build();
    }
}
