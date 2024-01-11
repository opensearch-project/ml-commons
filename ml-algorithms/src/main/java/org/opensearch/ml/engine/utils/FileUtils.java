/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.apache.commons.codec.digest.DigestUtils;
import org.opensearch.ml.common.exception.MLException;

import lombok.extern.log4j.Log4j2;

/**
 * A util class contains file related operations.
 */
@Log4j2
public class FileUtils {

    /**
     * Split file into smaller chunks evenly.
     * @param file file to be split
     * @param outputPath output path
     * @param chunkSize chunk size
     * @return a list of chunk file names
     * @throws IOException
     */
    public static List<String> splitFileIntoChunks(File file, Path outputPath, int chunkSize) throws IOException {
        int fileSize = (int) file.length();
        ArrayList<String> nameList = new ArrayList<>();
        try (InputStream inStream = new BufferedInputStream(new FileInputStream(file))) {
            int numberOfChunk = 0;
            int totalBytesRead = 0;
            while (totalBytesRead < fileSize) {
                String partName = numberOfChunk + "";
                int bytesRemaining = fileSize - totalBytesRead;
                if (bytesRemaining < chunkSize) {
                    chunkSize = bytesRemaining;
                }
                byte[] temporary = new byte[chunkSize];
                int bytesRead = inStream.read(temporary, 0, chunkSize);
                if (bytesRead > 0) {
                    totalBytesRead += bytesRead;
                    numberOfChunk++;
                }
                Path partFileName = outputPath.resolve(partName + "");
                write(temporary, partFileName.toString());
                nameList.add(partFileName.toString());
            }
        }
        return nameList;
    }

    /**
     * Write bytes to a file.
     * @param data bytes data
     * @param destinationFile destination file
     * @throws IOException
     */
    public static void write(byte[] data, String destinationFile) throws IOException {
        File file = new File(destinationFile);
        write(data, file, false);
    }

    /**
     * Write bytes into file by specifying append or not.
     * @param data bytes data
     * @param destinationFile destination file
     * @param append append bytes to file or not
     * @throws IOException
     */
    public static void write(byte[] data, File destinationFile, boolean append) throws IOException {
        org.apache.commons.io.FileUtils.forceMkdir(destinationFile.getParentFile());
        try (
            FileOutputStream fileOutputStream = new FileOutputStream(destinationFile, append);
            OutputStream output = new BufferedOutputStream(fileOutputStream)
        ) {
            output.write(data);
            output.flush();
        }
    }

    /**
     * Merge files into one big file.
     * @param files chunk files
     * @param mergedFile merged file
     */
    public static void mergeFiles(Queue<File> files, File mergedFile) {
        log.debug("merge {} files into {}", files.size(), mergedFile);
        boolean failed = false;
        while (!files.isEmpty()) {
            File f = files.poll();
            try (InputStream inStream = new BufferedInputStream(new FileInputStream(f))) {
                if (!failed) {
                    int fileLength = (int) f.length();
                    byte fileContent[] = new byte[fileLength];
                    inStream.read(fileContent, 0, fileLength);

                    write(fileContent, mergedFile, true);
                }
            } catch (IOException e) {
                log.error("Failed to merge file from " + f.getAbsolutePath() + " to " + mergedFile.getAbsolutePath(), e);
                failed = true;
            } finally {
                org.apache.commons.io.FileUtils.deleteQuietly(f);
                if (files.isEmpty()) {
                    org.apache.commons.io.FileUtils.deleteQuietly(f.getParentFile());
                }
            }
        }
        if (failed) {
            org.apache.commons.io.FileUtils.deleteQuietly(mergedFile);
            throw new MLException("Failed to merge model chunks");
        }
    }

    /**
     * Calculate sha256 hash value of file.
     * @param file file
     * @return sha256 hash value
     * @throws IOException
     */
    public static String calculateFileHash(File file) throws IOException {
        try (final InputStream inputStream = Files.newInputStream(file.toPath())) {
            return DigestUtils.sha256Hex(inputStream);
        }
    }

    /**
     * Delete file quietly.
     * @param path file path
     */
    public static void deleteFileQuietly(Path path) {
        deleteFileQuietly(new File(path.toUri()));
    }

    public static void deleteFileQuietly(File file) {
        if (file.exists()) {
            org.apache.commons.io.FileUtils.deleteQuietly(file);
        }
    }

    /**
     * Get all direct file names under list of paths, won't search recursively.
     * For example, we have "/tmp/subfolder1/1.txt", "/tmp/subfolder2/2.txt" and "/tmp/subfolder2/subfolder2-1/3.txt",
     * input is "/tmp/subfolder1" and "/tmp/subfolder2"
     * then will return "1.txt" and "2.txt", won't return "3.txt".
     * @param paths file paths
     * @return set of file name
     */
    public static Set<String> getFileNames(Path... paths) {
        Set<String> allFileNames = new HashSet<>();
        for (Path path : paths) {
            File f = new File(path.toUri());
            if (f.exists()) {
                String[] fileNames = f.list();
                if (fileNames != null && fileNames.length > 0) {
                    for (String name : fileNames) {
                        allFileNames.add(name);
                    }
                }
            }
        }
        return allFileNames;
    }
}
