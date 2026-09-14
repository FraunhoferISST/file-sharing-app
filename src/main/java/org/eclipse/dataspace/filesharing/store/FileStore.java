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

package org.eclipse.dataspace.filesharing.store;

import org.bson.types.ObjectId;
import org.eclipse.dataspace.filesharing.domain.FileMetadata;
import org.eclipse.dataspace.filesharing.exception.PersistenceException;
import org.eclipse.dataspace.filesharing.exception.ResourceNotFoundException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
public class FileStore {

    private final GridFsTemplate gridFsTemplate;
    private final FileMetadataRepository fileMetadataRepository;
    private final MongoTemplate mongoTemplate;

    public FileStore(GridFsTemplate gridFsTemplate, FileMetadataRepository fileMetadataRepository, MongoTemplate mongoTemplate) {
        this.gridFsTemplate = gridFsTemplate;
        this.fileMetadataRepository = fileMetadataRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Stores a file including relevant metadata for a given participant context.
     *
     * @param participantContextId the participant context id the file belongs to
     * @param file the file
     * @return the stored metadata
     */
    public FileMetadata save(String participantContextId, MultipartFile file) {
        ObjectId gridFileId;
        try {
            gridFileId = gridFsTemplate.store(file.getInputStream(), file.getOriginalFilename(), file.getContentType());
        } catch (IOException e) {
            throw new PersistenceException("Failed to persist file.", e);
        }

        var metadata = FileMetadata.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .participantContextId(participantContextId)
                .fileName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .uploadTimestamp(System.currentTimeMillis())
                .gridFsFileId(gridFileId.toString())
                .build();

        return fileMetadataRepository.save(metadata);
    }

    /**
     * Retrieves a list of metadata entries for a given participant context.
     *
     * @param participantContextId the participant context id
     * @return list of metadata entries
     */
    public List<FileMetadata> query(String participantContextId) {
        return fileMetadataRepository.findByParticipantContextId(participantContextId);
    }

    /**
     * Retrieves a metadata entry by id, if it belongs to the given participant context.
     *
     * @param participantContextId the participant context id
     * @param metadataId the metadata id
     * @return the metadata entry
     */
    public FileMetadata retrieveMetadata(String participantContextId, String metadataId) {
        var query = findByIdQuery(participantContextId, metadataId);
        var metadata = mongoTemplate.findOne(query, FileMetadata.class);
        if (metadata == null) {
            throw new ResourceNotFoundException("No metadata found for id %s".formatted(metadataId));
        }

        return metadata;
    }

    /**
     * Retrieves a file by metadata id, if it belongs to the given participant context.
     *
     * @param participantContextId the participant context id
     * @param metadataId the metadata id
     * @return the stored file
     */
    public GridFsResource retrieveFile(String participantContextId, String metadataId) {
        var query = findByIdQuery(participantContextId, metadataId);
        var metadata = mongoTemplate.findOne(query, FileMetadata.class);
        if (metadata == null) {
            throw new ResourceNotFoundException("No metadata found for id %s".formatted(metadataId));
        }

        var gridFsFile = gridFsTemplate.findOne(
                new Query(Criteria.where("_id").is(new ObjectId(metadata.getGridFsFileId())))
        );

        if (gridFsFile == null) {
            throw new ResourceNotFoundException("File not found for id %s".formatted(metadataId));
        }

        return gridFsTemplate.getResource(gridFsFile);
    }

    /**
     * Deletes a file and associated metadata by id, if it belongs to the given participant context.
     *
     * @param participantContextId the participant context id
     * @param metadataId the metadata id
     */
    public void delete(String participantContextId, String metadataId) {
        var query = findByIdQuery(participantContextId, metadataId);
        var metadata = mongoTemplate.findOne(query, FileMetadata.class);
        if (metadata == null) {
            throw new ResourceNotFoundException("No metadata found for id %s".formatted(metadataId));
        }

        gridFsTemplate.delete(
                new Query(Criteria.where("_id").is(new ObjectId(metadata.getGridFsFileId())))
        );

        fileMetadataRepository.deleteById(metadataId);
    }

    private Query findByIdQuery(String participantContextId, String metadataId) {
        var query = new Query();
        var idCriterion = Criteria.where("_id").is(new ObjectId(metadataId));
        var participantContextIdCriterion = Criteria.where("participantContextId").is(participantContextId);
        query.addCriteria(new Criteria().andOperator(idCriterion, participantContextIdCriterion));
        return query;
    }
}
