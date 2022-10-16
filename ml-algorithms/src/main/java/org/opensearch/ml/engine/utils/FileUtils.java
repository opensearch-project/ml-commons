/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.utils;

import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.nio.file.Path;

@Log4j2
public class FileUtils {

    /**
     * Delete file quietly.
     * @param path file path
     */
    public static void deleteFileQuietly(Path path) {
        File file = new File(path.toUri());
        if (file.exists()) {
            org.apache.commons.io.FileUtils.deleteQuietly(file);
        }
    }

}
