/*
 *  Copyright (c) 2026 Fraunhofer-Gesellschaft zur Förderung der angewandten Forschung e.V.
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Fraunhofer-Gesellschaft zur Förderung der angewandten Forschung e.V. - initial API and implementation
 *
 */

package org.eclipse.dataspace.filesharing.config;

import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jwt.SignedJWT;
import org.eclipse.dataspace.filesharing.exception.InvalidTokenException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URL;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean("sigletJwtDecoder")
    public JwtDecoder sigletJwtDecoder(@Value("${siglet.jwks.uri}") String sigletJwksUri) throws Exception {
        return token -> {
            try {
                var signedJWT = SignedJWT.parse(token);
                var jwkSet = JWKSet.load(new URL(sigletJwksUri));
                var jwk = jwkSet.getKeyByKeyId(signedJWT.getHeader().getKeyID());

                if (!(jwk instanceof OctetKeyPair okp)) {
                    throw new InvalidTokenException("No matching Ed25519 key found");
                }

                if (!signedJWT.verify(new Ed25519Verifier(okp))) {
                    throw new InvalidTokenException("JWT signature verification failed");
                }

                var claims = signedJWT.getJWTClaimsSet();

                return Jwt.withTokenValue(token)
                        .headers(h -> h.putAll(signedJWT.getHeader().toJSONObject()))
                        .issuedAt(claims.getIssueTime() != null ? claims.getIssueTime().toInstant() : null)
                        .expiresAt(claims.getExpirationTime() != null ? claims.getExpirationTime().toInstant() : null)
                        .notBefore(claims.getNotBeforeTime() != null ? claims.getNotBeforeTime().toInstant() : null)
                        .claims(c -> claims.getClaims().forEach((k, v) -> {
                            if (!"iat".equals(k) && !"exp".equals(k) && !"nbf".equals(k)) {
                                c.put(k, v);
                            }
                        }))
                        .build();
            } catch (Exception e) {
                throw new InvalidTokenException("Failed to decode JWT: " + e.getMessage(), e);
            }
        };
    }

    @Bean
    @Profile("!local")
    @Order(1)
    public SecurityFilterChain sigletFilterChain(HttpSecurity http, @Value("${siglet.jwks.uri}") String sigletJwksUri,
                                                 @Qualifier("sigletJwtDecoder") JwtDecoder jwtDecoder) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityMatcher("/api/dataplane/files", "/api/dataplane/files/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder)));
        return http.build();
    }

    @Bean("keycloakJwtDecoder")
    public JwtDecoder keycloakJwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    @Bean
    @Profile("!local")
    @Order(2)
    public SecurityFilterChain keycloakFilterChain(HttpSecurity http, @Qualifier("keycloakJwtDecoder") JwtDecoder jwtDecoder) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityMatcher(request -> !request.getServletPath().startsWith("/api/dataplane/files"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)));

        return http.build();
    }

    @Bean
    @Profile("local")
    public SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
