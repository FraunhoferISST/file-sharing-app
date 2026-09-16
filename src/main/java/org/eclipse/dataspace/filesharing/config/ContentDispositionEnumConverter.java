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

import org.eclipse.dataspace.filesharing.api.FileController;
import org.springframework.core.convert.converter.Converter;

public class ContentDispositionEnumConverter implements Converter<String, FileController.ContentDispositionValue> {
    @Override
    public FileController.ContentDispositionValue convert(String value) {
        return FileController.ContentDispositionValue.valueOf(value.toUpperCase());
    }
}
