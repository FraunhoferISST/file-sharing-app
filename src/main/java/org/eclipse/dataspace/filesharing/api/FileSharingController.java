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

import org.eclipse.dataspace.filesharing.store.FileStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;

@RestController
@RequestMapping("/api/dataplane/files")
public class FileSharingController {

    private static final String FILE_ID_CLAIM = "fileId";

    private final FileStore fileStore;
    private final String participantIdClaim;

    public FileSharingController(FileStore fileStore,
                                 @Value("${filesharing.claims.participant-id:participantId}") String participantIdClaim) {
        this.fileStore = fileStore;
        this.participantIdClaim = participantIdClaim;
    }

    @GetMapping
    public ResponseEntity<Resource> getFile(@AuthenticationPrincipal Jwt jwt) throws IOException {
        var participantContextId = jwt.getClaimAsString(participantIdClaim);
        var fileId = jwt.getClaimAsString(FILE_ID_CLAIM);

        var resource = fileStore.retrieveFile(participantContextId, fileId);
        var metadata = fileStore.retrieveMetadata(participantContextId, fileId);
        var contentType = metadata.getContentType();

        var contentDisposition = ContentDisposition.builder("attachment")
                .filename(resource.getFilename())
                .build();

        return ResponseEntity.ok()
                .header(CONTENT_DISPOSITION, contentDisposition.toString())
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(resource.contentLength())
                .body(resource);
    }

}
