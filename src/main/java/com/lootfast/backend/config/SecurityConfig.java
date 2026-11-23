package com.lootfast.backend.config;

import com.lootfast.backend.security.ApiSecurityErrorHandler;
import com.lootfast.backend.security.JwtRoleConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({CorsProperties.class, SocialAuthProperties.class})
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
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/admin/reports/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/premium/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/buyers/me/**").hasRole("BUYER")
                        .requestMatchers(
                                "/api/v1/auth/signup",
                                "/api/v1/auth/signin",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/verify-otp",
                                "/api/v1/auth/resend-otp",
                                "/api/v1/auth/password-reset/request",
                                "/api/v1/auth/password-reset/confirm",
                                "/api/v1/auth/social/nonce",
                                "/api/v1/auth/social/login",
                                "/actuator/health",
                                "/actuator/health/**",
                                // WebSocket handshake cannot carry a bearer header from browsers;
                                // the JWT is verified on the STOMP CONNECT frame instead
                                // (see StompAuthChannelInterceptor).
                                "/ws",
                                "/v3/api-docs/**",
                                "/swagger-ui",
                                "/swagger-ui.html",
                                "/swagger-ui/**"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/deals/**",
                                "/api/v1/stores/**",
                                "/api/v1/payments/vnpay/ipn",
                                "/api/v1/payments/vnpay/return",
                                "/api/v1/premium/plans",
                                "/api/v1/premium/plans/**",
                                "/api/v1/categories",
                                "/api/v1/banners",
                                "/api/v1/legal-documents/**"
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

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()
        ));
        configuration.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT,
                "Idempotency-Key"
        ));
        configuration.setExposedHeaders(List.of("X-Trace-Id"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
