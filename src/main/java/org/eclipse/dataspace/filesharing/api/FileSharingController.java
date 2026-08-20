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

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;

@RestController
@RequestMapping("/api/dataplane/files")
public class FileSharingController {

    //TODO accept Siglet's auth token

    @RequestMapping("/push/{participantContextId}/{id}")
    public ResponseEntity<Object> pushFile(@PathVariable("participantContextId") String participantContextId,
                                           @PathVariable("id") String id, @RequestParam("file") MultipartFile file) {
        //TODO
        return ResponseEntity.ok().build();
    }

    @RequestMapping("/pull/{participantContextId}/{id}")
    public ResponseEntity<Resource> pullFile(@PathVariable("participantContextId") String participantContextId,
                                             @PathVariable("id") String id) throws IOException {
        //TODO
        var resource = new ClassPathResource("files/test.json");
        var contentDisposition = ContentDisposition.builder("attachment")
                .filename(resource.getFilename())
                .build();

        return ResponseEntity.ok()
                .header(CONTENT_DISPOSITION, contentDisposition.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(resource.contentLength())
                .body(resource);
    }
}
