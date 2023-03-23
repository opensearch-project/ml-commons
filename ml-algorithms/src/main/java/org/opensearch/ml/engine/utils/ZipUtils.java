/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.utils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import lombok.extern.log4j.Log4j2;

/**
 * A util class contains file related operations.
 */
@Log4j2
public class ZipUtils {
    public static void unzip(File zipFile, Path dest) {
        try {
            ZipFile file = new ZipFile(zipFile);
            Enumeration<ZipArchiveEntry> en = file.getEntries();
            ZipArchiveEntry ze;
            while (en.hasMoreElements()) {
                ze = en.nextElement();
                String name = ze.getName();
                if (name.contains("..")) {
                    throw new IOException("Malicious zip entry: " + name);
                }
                Path f = dest.resolve(name).toAbsolutePath();
                if (ze.isDirectory()) {
                    Files.createDirectories(f);
                } else {
                    Path parentFile = f.getParent();
                    if (parentFile == null) {
                        throw new AssertionError(
                                "Parent path should never be null: " + f);
                    }
                    Files.createDirectories(parentFile);
                    InputStream is = file.getInputStream(ze);
                    Files.copy(is, f, StandardCopyOption.REPLACE_EXISTING);
                    is.close();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
