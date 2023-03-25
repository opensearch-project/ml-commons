/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import lombok.extern.log4j.Log4j2;

/**
 * A util class contains zip file related operations.
 */
@Log4j2
public class ZipUtils {

    /**
     * Uncompressed a zip file.
     * @param zipFile zip file to be uncompressed
     * @param dest the destination path of this uncompress
     */
    public static void unzip(File zipFile, Path dest) {
        try {
            ZipFile unzipFile = new ZipFile(zipFile);
            Enumeration<ZipArchiveEntry> en = unzipFile.getEntries();
            ZipArchiveEntry zipEntry;
            while (en.hasMoreElements()) {
                zipEntry = en.nextElement();
                String name = zipEntry.getName();
                Path file = dest.resolve(name).toAbsolutePath();
                if (!file.normalize().startsWith(dest.toAbsolutePath()))
                    throw new RuntimeException("Bad zip entry");
                if (zipEntry.isDirectory()) {
                    Files.createDirectories(file);
                } else {
                    Path parentFile = file.getParent();
                    if (parentFile == null) {
                        throw new AssertionError(
                                "Parent path should never be null: " + file);
                    }
                    Files.createDirectories(parentFile);
                    InputStream inputStream = unzipFile.getInputStream(zipEntry);
                    Files.copy(inputStream, file, StandardCopyOption.REPLACE_EXISTING);
                    inputStream.close();
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Wrong input file", e);
        }
    }
}
