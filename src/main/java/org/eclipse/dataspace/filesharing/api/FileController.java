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

package org.eclipse.dataspace.filesharing.api;

import org.eclipse.dataspace.filesharing.domain.FileMetadata;
import org.eclipse.dataspace.filesharing.exception.ParticipantContextMismatchException;
import org.eclipse.dataspace.filesharing.store.FileStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileController.class);

    private final FileStore fileStore;
    private final ObjectMapper objectMapper;
    private final String participantIdClaim;

    public FileController(FileStore fileStore, ObjectMapper objectMapper,
                          @Value("${filesharing.claims.participant-id:participant_context_id}") String participantIdClaim) {
        this.fileStore = fileStore;
        this.objectMapper = objectMapper;
        this.participantIdClaim = participantIdClaim;
    }

    @PostMapping("/{participantContextId}")
    public ResponseEntity<FileMetadata> uploadFile(@PathVariable("participantContextId") String participantContextId,
                                                   @RequestParam("file") MultipartFile file,
                                                   @RequestParam(value = "metadata", required = false) String metadataJson,
                                                   @AuthenticationPrincipal Jwt jwt) throws IOException {
        var authenticatedParticipantId = participantId(jwt, participantContextId);
        LOGGER.debug("Upload request for participant context '{}' file '{}' metadata JSON = {}", authenticatedParticipantId, file.getOriginalFilename(), metadataJson);

        JsonNode metadata = parseMetadata(metadataJson);
        var storedMetadata = fileStore.save(authenticatedParticipantId, file, metadata);

        return ResponseEntity.ok(storedMetadata);
    }

    private JsonNode parseMetadata(String metadataJson) throws IOException {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        return objectMapper.readTree(metadataJson);
    }

    @GetMapping("/{participantContextId}")
    public ResponseEntity<List<FileMetadata>> getAll(@PathVariable("participantContextId") String participantContextId,
                                                     @AuthenticationPrincipal Jwt jwt) {
        var metadataEntries = fileStore.getAll(participantId(jwt, participantContextId));

        return ResponseEntity.ok(metadataEntries);
    }

    @GetMapping("/{participantContextId}/{id}/metadata")
    public ResponseEntity<FileMetadata> getFileMetadata(@PathVariable("participantContextId") String participantContextId,
                                                        @PathVariable("id") String id,
                                                        @AuthenticationPrincipal Jwt jwt) {
        var metadata = fileStore.retrieveMetadata(participantId(jwt, participantContextId), id);
        return ResponseEntity.ok(metadata);
    }

    @GetMapping("/{participantContextId}/{id}")
    public ResponseEntity<Resource> downloadFile(@PathVariable("participantContextId") String participantContextId,
                                                 @PathVariable("id") String id,
                                                 @RequestParam(name = "disposition", defaultValue = "attachment") ContentDispositionValue disposition,
                                                 @AuthenticationPrincipal Jwt jwt) throws IOException {
        var authenticatedParticipantId = participantId(jwt, participantContextId);
        var resource = fileStore.retrieveFile(authenticatedParticipantId, id);

        //use "attachment" for automatic download; use "inline" to show file in browser
        var contentDisposition = ContentDisposition.builder(disposition.name().toLowerCase())
                .filename(resource.getFilename())
                .build();

        var metadata = fileStore.retrieveMetadata(authenticatedParticipantId, id);
        var contentType = metadata.getContentType();

        return ResponseEntity.ok()
                .header(CONTENT_DISPOSITION, contentDisposition.toString())
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(resource.contentLength())
                .body(resource);
    }

    @DeleteMapping("/{participantContextId}/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable("participantContextId") String participantContextId,
                                           @PathVariable("id") String id,
                                           @AuthenticationPrincipal Jwt jwt) {
        fileStore.delete(participantId(jwt, participantContextId), id);

        return ResponseEntity.noContent().build();
    }

    private String participantId(Jwt jwt, String pathParticipantContextId) {
        var participantId = jwt.getClaimAsString(participantIdClaim);
        if (participantId == null || participantId.isBlank()) {
            throw new IllegalStateException("JWT is missing required claim '%s'".formatted(participantIdClaim));
        }
        if (!participantId.equals(pathParticipantContextId)) {
            throw new ParticipantContextMismatchException();
        }
        return participantId;
    }

    public enum ContentDispositionValue {
        ATTACHMENT, INLINE
    }
}
