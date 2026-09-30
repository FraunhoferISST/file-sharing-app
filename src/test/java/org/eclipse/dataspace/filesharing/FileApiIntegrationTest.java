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

import com.jayway.jsonpath.JsonPath;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FileApiIntegrationTest {

    private final String participantContextId = "pc-1";

    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:8.0.4");

    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void clearMongo() {
        mongoTemplate.getCollectionNames().forEach(collection ->
                mongoTemplate.getCollection(collection).deleteMany(new Document()));
    }

    @Nested
    class Upload {
        @Test
        void uploadFile_shouldUploadFile() throws Exception {
            var file = new MockMultipartFile("file", "file.txt", "text/plain", "hello world".getBytes());

            mockMvc.perform(multipart("/api/files/{participantContextId}", participantContextId).file(file)
                            .with(jwt().jwt(jwtBuilder -> jwtBuilder.claim("participant_context_id", participantContextId))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.participantContextId").value(participantContextId))
                    .andExpect(jsonPath("$.fileName").value("file.txt"))
                    .andExpect(jsonPath("$.contentType").value("text/plain"))
                    .andExpect(jsonPath("$.contentLength").value(greaterThan(0)))
                    .andExpect(jsonPath("$.gridFsFileId").value(notNullValue()));
        }
    }

    @Nested
    class GetAll {
        @Test
        void getAll_shouldReturnFilesForParticipantContext() throws Exception {
            var file1 = new MockMultipartFile("file", "file1.txt", "text/plain", "hello world".getBytes());
            var file2 = new MockMultipartFile("file", "file2.txt", "text/plain", "hello moon".getBytes());

            mockMvc.perform(multipart("/api/files/{participantContextId}", participantContextId).file(file1)
                    .with(jwt().jwt(jwtBuilder -> jwtBuilder.claim("participant_context_id", participantContextId))));
            mockMvc.perform(multipart("/api/files/{participantContextId}", participantContextId).file(file2)
                    .with(jwt().jwt(jwtBuilder -> jwtBuilder.claim("participant_context_id", participantContextId))));

            mockMvc.perform(get("/api/files/{participantContextId}", participantContextId)
                            .with(jwt().jwt(jwtBuilder -> jwtBuilder.claim("participant_context_id", participantContextId))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[*].fileName", containsInAnyOrder("file1.txt", "file2.txt")));
        }

        @Test
        void getAll_differentParticipantContexts_shouldOnlyReturnOwnFiles() throws Exception {
            var otherParticipantContextId = "pc-2";

            var file1 = new MockMultipartFile("file", "file1.txt", "text/plain", "hello world".getBytes());
            mockMvc.perform(multipart("/api/files/{participantContextId}", participantContextId).file(file1)
                    .with(jwt().jwt(jwtBuilder -> jwtBuilder.claim("participant_context_id", participantContextId))));

            var file2 = new MockMultipartFile("file", "file2.txt", "text/plain", "hello moon".getBytes());
            mockMvc.perform(multipart("/api/files/{participantContextId}", otherParticipantContextId).file(file2)
                    .with(jwt().jwt(jwtBuilder -> jwtBuilder.claim("participant_context_id", otherParticipantContextId))));

            mockMvc.perform(get("/api/files/{participantContextId}", participantContextId)
                            .with(jwt().jwt(jwtBuilder -> jwtBuilder.claim("participant_context_id", participantContextId))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].fileName").value("file1.txt"));
        }
    }

    @Nested
    class GetMetadata {
        @Test
        void getFileMetadata_shouldReturnFileMetadata() throws Exception {
            var file = new MockMultipartFile("file", "file.txt", "text/plain", "hello world".getBytes());
            var mvcResult = mockMvc.perform(multipart("/api/files/{participantContextId}", participantContextId).file(file)
                            .with(jwt().jwt(jwtBuilder -> jwtBuilder.claim("participant_context_id", participantContextId))))
                    .andReturn();
            var id = JsonPath.read(mvcResult.getResponse().getContentAsString(), "$.id");

            mockMvc.perform(get("/api/files/{participantContextId}/{id}/metadata", participantContextId, id)
                            .with(jwt().jwt(jwtBuilder -> jwtBuilder.claim("participant_context_id", participantContextId))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.participantContextId").value(participantContextId))
                    .andExpect(jsonPath("$.fileName").value("file.txt"))
                    .andExpect(jsonPath("$.contentType").value("text/plain"))
                    .andExpect(jsonPath("$.contentLength").value(greaterThan(0)))
                    .andExpect(jsonPath("$.gridFsFileId").value(notNullValue()));
        }

        @Test
        void getFileMetadata_fileBelongsToDifferentParticipantContext_shouldReturnNotFound() throws Exception {
            var otherParticipantContextId = "pc-2";

            var file = new MockMultipartFile("file", "file.txt", "text/plain", "hello world".getBytes());
            var mvcResult = mockMvc.perform(multipart("/api/files/{participantContextId}", participantContextId).file(file)
                            .with(jwt().jwt(jwtBuilder -> jwtBuilder.claim("participant_context_id", participantContextId))))
                    .andReturn();
            var id = JsonPath.read(mvcResult.getResponse().getContentAsString(), "$.id");

            mockMvc.perform(get("/api/files/{participantContextId}/{id}/metadata", otherParticipantContextId, id)
                            .with(jwt().jwt(jwtBuilder -> jwtBuilder.claim("participant_context_id", otherParticipantContextId))))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class Download {
        @Test
        void downloadFile_shouldReturnFile() throws Exception {
            var file = new MockMultipartFile("file", "file.txt", "text/plain", "hello world".getBytes());
            var mvcResult = mockMvc.perform(multipart("/api/files/{participantContextId}", participantContextId).file(file)
                            .with(jwt().jwt(jwtBuilder -> jwtBuilder.claim("participant_context_id", participantContextId))))
                    .andReturn();
            var id = JsonPath.read(mvcResult.getResponse().getContentAsString(), "$.id");

            mockMvc.perform(get("/api/files/{participantContextId}/{id}", participantContextId, id)
                            .with(jwt().jwt(jwtBuilder -> jwtBuilder.claim("participant_context_id", participantContextId))))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition", "attachment; filename=\"file.txt\""))
                    .andExpect(content().contentType("text/plain"))
                    .andExpect(content().bytes("hello world".getBytes()));
        }

        @Test
        void downloadFile_fileBelongsToDifferentParticipantContext_shouldReturnNotFound() throws Exception {
            var otherParticipantContextId = "pc-2";

            var file = new MockMultipartFile("file", "file.txt", "text/plain", "hello world".getBytes());
            var mvcResult = mockMvc.perform(multipart("/api/files/{participantContextId}", participantContextId).file(file)
                            .with(jwt().jwt(jwtBuilder -> jwtBuilder.claim("participant_context_id", participantContextId))))
                    .andReturn();
            var id = JsonPath.read(mvcResult.getResponse().getContentAsString(), "$.id");

            mockMvc.perform(get("/api/files/{participantContextId}/{id}", otherParticipantContextId, id)
                            .with(jwt().jwt(jwtBuilder -> jwtBuilder.claim("participant_context_id", otherParticipantContextId))))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class Delete {
        @Test
        void deleteFile_shouldDeleteFile() throws Exception {
            var file = new MockMultipartFile("file", "file.txt", "text/plain", "hello world".getBytes());
            var mvcResult = mockMvc.perform(multipart("/api/files/{participantContextId}", participantContextId).file(file)
                            .with(jwt().jwt(jwtBuilder -> jwtBuilder.claim("participant_context_id", participantContextId))))
                    .andReturn();
            var id = JsonPath.read(mvcResult.getResponse().getContentAsString(), "$.id");

            mockMvc.perform(get("/api/files/{participantContextId}/{id}/metadata", participantContextId, id)
                            .with(jwt().jwt(jwtBuilder -> jwtBuilder.claim("participant_context_id", participantContextId))))
                    .andExpect(status().isOk());

            mockMvc.perform(delete("/api/files/{participantContextId}/{id}", participantContextId, id)
                            .with(jwt().jwt(jwtBuilder -> jwtBuilder.claim("participant_context_id", participantContextId))))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/files/{participantContextId}/{id}/metadata", participantContextId, id)
                            .with(jwt().jwt(jwtBuilder -> jwtBuilder.claim("participant_context_id", participantContextId))))
                    .andExpect(status().isNotFound());
        }

        @Test
        void deleteFile_fileBelongsToDifferentParticipantContext_shouldReturnNotFound() throws Exception {
            var otherParticipantContextId = "pc-2";

            var file = new MockMultipartFile("file", "file.txt", "text/plain", "hello world".getBytes());
            var mvcResult = mockMvc.perform(multipart("/api/files/{participantContextId}", participantContextId).file(file)
                            .with(jwt().jwt(jwtBuilder -> jwtBuilder.claim("participant_context_id", participantContextId))))
                    .andReturn();
            var id = JsonPath.read(mvcResult.getResponse().getContentAsString(), "$.id");

            mockMvc.perform(delete("/api/files/{participantContextId}/{id}", otherParticipantContextId, id)
                            .with(jwt().jwt(jwtBuilder -> jwtBuilder.claim("participant_context_id", otherParticipantContextId))))
                    .andExpect(status().isNotFound());
        }
    }
}
