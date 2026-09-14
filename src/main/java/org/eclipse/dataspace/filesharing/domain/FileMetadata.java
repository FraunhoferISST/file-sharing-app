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

package org.eclipse.dataspace.filesharing.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Objects;

@Document(collection = "file_metadata")
public class FileMetadata {

    @Id
    private String id;
    private String participantContextId;

    private String fileName;
    private String contentType;
    private long contentLength;
    private long uploadTimestamp;

    private String gridFsFileId;

    public String getId() {
        return id;
    }

    public String getParticipantContextId() {
        return participantContextId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getUploadTimestamp() {
        return uploadTimestamp;
    }

    public long getContentLength() {
        return contentLength;
    }

    public String getGridFsFileId() {
        return gridFsFileId;
    }

    public static class Builder {
        private final FileMetadata fileMetadata;

        private Builder() {
            this.fileMetadata = new FileMetadata();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder id(String id) {
            this.fileMetadata.id = id;
            return this;
        }

        public Builder participantContextId(String participantContextId) {
            this.fileMetadata.participantContextId = participantContextId;
            return this;
        }

        public Builder fileName(String fileName) {
            this.fileMetadata.fileName = fileName;
            return this;
        }

        public Builder contentType(String contentType) {
            this.fileMetadata.contentType = contentType;
            return this;
        }

        public Builder contentLength(long contentLength) {
            this.fileMetadata.contentLength = contentLength;
            return this;
        }

        public Builder uploadTimestamp(long uploadTimestamp) {
            this.fileMetadata.uploadTimestamp = uploadTimestamp;
            return this;
        }

        public Builder gridFsFileId(String gridFsFileId) {
            this.fileMetadata.gridFsFileId = gridFsFileId;
            return this;
        }

        public FileMetadata build() {
            Objects.requireNonNull(fileMetadata.id, "file metadata: id is required");
            Objects.requireNonNull(fileMetadata.participantContextId, "file metadata: participantContextId is required");
            Objects.requireNonNull(fileMetadata.gridFsFileId, "file metadata: gridFsFileId is required");

            return this.fileMetadata;
        }
    }

}
