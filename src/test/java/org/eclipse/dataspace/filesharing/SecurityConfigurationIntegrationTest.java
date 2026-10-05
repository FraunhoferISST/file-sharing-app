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

package org.eclipse.dataspace.filesharing;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.eclipse.dataspace.filesharing.domain.FileMetadata;
import org.eclipse.dataspace.filesharing.store.FileStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigurationIntegrationTest {

    private static final String SIGLET_KEY_ID = "siglet-test-key";
    private static final String KEYCLOAK_KEY_ID = "keycloak-test-key";

    static WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileStore fileStore;

    private static OctetKeyPair sigletKey;
    private static OctetKeyPair wrongSigletKey;
    private static RSAKey keycloakKey;
    private static RSAKey wrongKeycloakKey;

    @BeforeAll
    static void setUp() throws JOSEException {
        wireMock.start();

        sigletKey = new OctetKeyPairGenerator(com.nimbusds.jose.jwk.Curve.Ed25519)
                .keyID(SIGLET_KEY_ID)
                .generate();
        wrongSigletKey = new OctetKeyPairGenerator(com.nimbusds.jose.jwk.Curve.Ed25519).generate();
        keycloakKey = new RSAKeyGenerator(2048).keyID(KEYCLOAK_KEY_ID).generate();
        wrongKeycloakKey = new RSAKeyGenerator(2048).generate();
    }

    @AfterAll
    static void tearDown() {
        wireMock.stop();
    }

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("siglet.jwks.uri", () -> "http://localhost:" + wireMock.port() + "/siglet/keys");
        registry.add("siglet.token.issuer", () -> "siglet-issuer");

        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:" + wireMock.port() + "/keycloak/keys");
    }

    @BeforeEach
    void initMocks() throws Exception {
        wireMock.resetAll();
        wireMock.stubFor(WireMock.get(urlEqualTo("/siglet/keys"))
                .willReturn(okJson(new JWKSet(sigletKey.toPublicJWK()).toString())));
        wireMock.stubFor(WireMock.get(urlEqualTo("/keycloak/keys"))
                .willReturn(okJson(new JWKSet(keycloakKey.toPublicJWK()).toString())));

        var fileResource = mock(GridFsResource.class);
        when(fileResource.contentLength()).thenReturn(1234L);
        when(fileResource.getFilename()).thenReturn("file.json");
        when(fileResource.getContent()).thenReturn(new ByteArrayInputStream("content".getBytes()));

        when(fileStore.retrieveFile(anyString(), anyString())).thenReturn(fileResource);
        when(fileStore.retrieveMetadata(anyString(), anyString())).thenReturn(
               FileMetadata.Builder.newInstance()
                        .id("file-1")
                        .participantContextId("pc-1")
                        .fileName("file.json")
                        .contentType("application/json")
                        .contentLength(1234L)
                        .uploadTimestamp(System.currentTimeMillis())
                        .gridFsFileId("grid-file-id")
                        .build()
        );
        when(fileStore.getAll(anyString())).thenReturn(List.of());
    }

    @Nested
    class KeycloakToken {
        @Test
        void validKeycloakToken_shouldBeAcceptedByFileApi() throws Exception {
            mockMvc.perform(get("/api/files/pc-1")
                            .header("Authorization", "Bearer " + keycloakToken(keycloakKey, false)))
                    .andExpect(status().isOk());
        }

        @Test
        void validKeycloakToken_shouldBeRejectedByDataplaneApi() throws Exception {
            mockMvc.perform(get("/api/dataplane/files")
                            .header("Authorization", "Bearer " + keycloakToken(keycloakKey, false)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void expiredKeycloakToken_shouldBeRejected() throws Exception {
            mockMvc.perform(get("/api/files/pc-1")
                            .header("Authorization", "Bearer " + keycloakToken(keycloakKey, true)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void invalidKeycloakSignature_shouldBeRejected() throws Exception {
            mockMvc.perform(get("/api/files/pc-1")
                            .header("Authorization", "Bearer " + keycloakToken(wrongKeycloakKey, false)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class SigletToken {
        @Test
        void validSigletToken_shouldBeRejectedByFileApi() throws Exception {
            mockMvc.perform(get("/api/files/pc-1")
                            .header("Authorization", "Bearer " + sigletToken(sigletKey, false)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void validSigletToken_shouldBeAcceptedByDataplaneApi() throws Exception {
            mockMvc.perform(get("/api/dataplane/files")
                            .header("Authorization", "Bearer " + sigletToken(sigletKey, false)))
                    .andExpect(status().isOk());
        }

        @Test
        void expiredSigletToken_shouldBeRejected() throws Exception {
            mockMvc.perform(get("/api/dataplane/files")
                            .header("Authorization", "Bearer " + sigletToken(sigletKey, true)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void invalidSigletSignature_shouldBeRejected() throws Exception {
            mockMvc.perform(get("/api/dataplane/files")
                            .header("Authorization", "Bearer " + sigletToken(wrongSigletKey, false)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void invalidSigletIssuer_shouldBeRejected() throws Exception {
            mockMvc.perform(get("/api/dataplane/files")
                            .header("Authorization", "Bearer " + sigletToken(sigletKey, false, "invalid-issuer")))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void missingToken_shouldBeRejectedByBothApis() throws Exception {
        mockMvc.perform(get("/api/dataplane/files"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/files/pc-1"))
                .andExpect(status().isUnauthorized());
    }

    private static String sigletToken(OctetKeyPair signingKey, boolean expired) throws JOSEException {
        return sigletToken(signingKey, expired, "siglet-issuer");
    }

    private static String sigletToken(OctetKeyPair signingKey, boolean expired, String issuer) throws JOSEException {
        var now = Instant.now();
        var claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("siglet-test-client")
                .issueTime(Date.from(now.minusSeconds(expired ? 300 : 60)))
                .expirationTime(Date.from(expired ? now.minusSeconds(120) : now.plusSeconds(300)))
                .claim("participantContextId", "pc-1")
                .claim("fileId", "missing-file")
                .build();
        var header = new JWSHeader.Builder(JWSAlgorithm.EdDSA)
                .keyID(SIGLET_KEY_ID)
                .type(JOSEObjectType.JWT)
                .build();

        return sign(header, claims, new Ed25519Signer(signingKey));
    }

    private static String keycloakToken(RSAKey signingKey, boolean expired) throws JOSEException {
        var now = Instant.now();
        var claims = new JWTClaimsSet.Builder()
                .issuer("https://keycloak.example.test/realms/test")
                .subject("keycloak-test-user")
                .issueTime(Date.from(now.minusSeconds(expired ? 300 : 60)))
                .expirationTime(Date.from(expired ? now.minusSeconds(120) : now.plusSeconds(300)))
                .claim("participant_context_id", "pc-1")
                .build();
        var header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(KEYCLOAK_KEY_ID)
                .type(JOSEObjectType.JWT)
                .build();

        return sign(header, claims, new RSASSASigner(signingKey));
    }

    private static String sign(JWSHeader header, JWTClaimsSet claims, JWSSigner signer) throws JOSEException {
        var jwt = new SignedJWT(header, claims);
        jwt.sign(signer);
        return jwt.serialize();
    }
}
