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

package org.eclipse.dataspace.filesharing.token;

import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jwt.SignedJWT;
import org.eclipse.dataspace.filesharing.exception.InvalidTokenException;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.stereotype.Component;

import java.net.URL;

/**
 * JWT decoder for handling Siglet-issued tokens. This is required, as Spring Security does not
 * currently support EdDSA. Uses Nimbus to decode and verify the incoming token, and constructs a
 * Spring Security JWT from it.
 */
@Component("sigletJwtDecoder")
public class SigletJwtDecoder implements JwtDecoder {

    private final String sigletJwksUri;
    private final String expectedSigletIssuer;

    private final JwtTimestampValidator timestampValidator;

    public SigletJwtDecoder(@Value("${siglet.jwks.uri}") String sigletJwksUri,
                            @Value("${siglet.token.issuer}") String expectedSigletIssuer) {
        this.sigletJwksUri = sigletJwksUri;
        this.expectedSigletIssuer = expectedSigletIssuer;
        this.timestampValidator = new JwtTimestampValidator();
    }

    @Override
    public @NotNull Jwt decode(String token) throws JwtException {
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

            if (!expectedSigletIssuer.equals(claims.getIssuer())) {
                throw new InvalidTokenException("JWT issuer does not match");
            }

            var jwt = Jwt.withTokenValue(token)
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

            var validationResult = timestampValidator.validate(jwt);
            if (validationResult.hasErrors()) {
                throw new InvalidTokenException(validationResult.getErrors().iterator().next().getDescription());
            }

            return jwt;
        } catch (Exception e) {
            throw new InvalidTokenException("Failed to decode or verify JWT: " + e.getMessage(), e);
        }
    }
}
