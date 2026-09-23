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

package org.eclipse.dataspace.filesharing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Configuration
public class MongoConfig {

    @Bean
    public GridFsTemplate gridFsTemplate(MongoTemplate mongoTemplate, MongoConverter mongoConverter) {
        return new GridFsTemplate(mongoTemplate.getMongoDatabaseFactory(), mongoConverter);
    }

    @Bean
    public MongoCustomConversions mongoCustomConversions(ObjectMapper objectMapper) {
        return new MongoCustomConversions(List.of(
                new JsonNodeToStringConverter(objectMapper),
                new StringToJsonNodeConverter(objectMapper)
        ));
    }

    @WritingConverter
    static class JsonNodeToStringConverter implements Converter<JsonNode, String> {

        private final ObjectMapper objectMapper;

        JsonNodeToStringConverter(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public String convert(JsonNode source) {
            return objectMapper.writeValueAsString(source);
        }
    }

    @ReadingConverter
    static class StringToJsonNodeConverter implements Converter<String, JsonNode> {

        private final ObjectMapper objectMapper;

        StringToJsonNodeConverter(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public JsonNode convert(String source) {
            return objectMapper.readTree(source);
        }
    }

}
