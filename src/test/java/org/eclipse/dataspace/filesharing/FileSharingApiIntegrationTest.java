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

import org.bson.Document;
import org.eclipse.dataspace.filesharing.domain.FileMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.security.oauth2.jwt.Jwt.Builder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.util.UUID;
import java.util.function.Consumer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FileSharingApiIntegrationTest {

    private final String participantContextId = "pc-1";

    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:8.0.4");

    @Autowired
    private GridFsTemplate gridFsTemplate;
    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void clearMongo() {
        mongoTemplate.getCollectionNames().forEach(collection ->
                mongoTemplate.getCollection(collection).deleteMany(new Document()));
    }

    @Test
    void getFile_shouldReturnFile() throws Exception {
        var fileId = storeFile();

        mockMvc.perform(get("/api/dataplane/files")
                        .with(jwt().jwt(jwtBuilder(participantContextId, fileId))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"test-file.txt\""))
                .andExpect(content().contentType("text/plain"))
                .andExpect(content().bytes("hello world".getBytes()));
    }

    @Test
    void getFile_fileDoesNotExist_shouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/dataplane/files")
                        .with(jwt().jwt(jwtBuilder(participantContextId, "non-existent-file-id"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getFile_participantContextIdClaimMissing_shouldReturnNotFound() throws Exception {
        var fileId = storeFile();

        mockMvc.perform(get("/api/dataplane/files")
                        .with(jwt().jwt(jwtBuilder -> jwtBuilder.claim("fileId", fileId))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getFile_wrongParticipantContextId_shouldReturnNotFound() throws Exception {
        var fileId = storeFile();

        mockMvc.perform(get("/api/dataplane/files")
                        .with(jwt().jwt(jwtBuilder("wrong-participant-context-id", fileId))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getFile_fileIdClaimMissing_shouldReturnNotFound() throws Exception {
        storeFile();

        mockMvc.perform(get("/api/dataplane/files")
                        .with(jwt().jwt(jwtBuilder -> jwtBuilder.claim("participantContextId", participantContextId))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getFile_wrongFileId_shouldReturnNotFound() throws Exception {
        storeFile();

        mockMvc.perform(get("/api/dataplane/files")
                        .with(jwt().jwt(jwtBuilder(participantContextId, "wrong-file-id"))))
                .andExpect(status().isNotFound());
    }

    private Consumer<Builder> jwtBuilder(String participantContextId, String fileId) {
        return jwtBuilder -> jwtBuilder
                .claim("participantContextId", participantContextId)
                .claim("fileId", fileId);
    }

    private String storeFile() {
        byte[] fileContent = "hello world".getBytes();
        var gridFsObjectId = gridFsTemplate.store(
                new ByteArrayInputStream(fileContent),
                "test-file.txt",
                "text/plain"
        );

        var fileMetadata = FileMetadata.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .participantContextId(participantContextId)
                .fileName("test-file.txt")
                .contentType("text/plain")
                .contentLength(fileContent.length)
                .uploadTimestamp(System.currentTimeMillis())
                .gridFsFileId(gridFsObjectId.toString())
                .build();
        mongoTemplate.save(fileMetadata);

        return fileMetadata.getId();
    }
}
