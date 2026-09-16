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
import org.eclipse.dataspace.filesharing.store.FileStore;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileStore fileStore;

    public FileController(FileStore fileStore) {
        this.fileStore = fileStore;
    }

    @PostMapping("/{participantContextId}")
    public ResponseEntity<FileMetadata> uploadFile(@PathVariable("participantContextId") String participantContextId,
                                                   @RequestParam("file") MultipartFile file) {
        var storedMetadata = fileStore.save(participantContextId, file);

        return ResponseEntity.ok(storedMetadata);
    }

    @GetMapping("/{participantContextId}")
    public ResponseEntity<List<FileMetadata>> getAll(@PathVariable("participantContextId") String participantContextId) {
        var metadataEntries = fileStore.getAll(participantContextId);

        return ResponseEntity.ok(metadataEntries);
    }

    @GetMapping("/{participantContextId}/{id}/metadata")
    public ResponseEntity<FileMetadata> getFileMetadata(@PathVariable("participantContextId") String participantContextId,
                                                        @PathVariable("id") String id) {
        var metadata = fileStore.retrieveMetadata(participantContextId, id);
        return ResponseEntity.ok(metadata);
    }

    @GetMapping("/{participantContextId}/{id}")
    public ResponseEntity<Resource> downloadFile(@PathVariable("participantContextId") String participantContextId,
                                                 @PathVariable("id") String id,
                                                 @RequestParam(name = "disposition", defaultValue = "attachment") ContentDispositionValue disposition) throws IOException {
        var resource = fileStore.retrieveFile(participantContextId, id);

        //use "attachment" for automatic download; use "inline" to show file in browser
        var contentDisposition = ContentDisposition.builder(disposition.name().toLowerCase())
                .filename(resource.getFilename())
                .build();

        var metadata = fileStore.retrieveMetadata(participantContextId, id);
        var contentType = metadata.getContentType();

        return ResponseEntity.ok()
                .header(CONTENT_DISPOSITION, contentDisposition.toString())
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(resource.contentLength())
                .body(resource);
    }

    @DeleteMapping("/{participantContextId}/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable("participantContextId") String participantContextId,
                                           @PathVariable("id") String id) {
        fileStore.delete(participantContextId, id);

        return ResponseEntity.noContent().build();
    }

    public enum ContentDispositionValue {
        ATTACHMENT, INLINE
    }
}
