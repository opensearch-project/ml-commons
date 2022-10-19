/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.utils;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import lombok.extern.log4j.Log4j2;
import org.opensearch.ml.common.exception.MLException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        try (InputStream inStream = new BufferedInputStream(new FileInputStream(file))){
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
        org.apache.commons.io.FileUtils.createParentDirectories(destinationFile);
        try (OutputStream output = new BufferedOutputStream(new FileOutputStream(destinationFile, append))){
            output.write(data);
        }
    }

    /**
     * Merge files into one big file.
     * @param files array of files
     * @param mergedFile merged file
     */
    public static void mergeFiles(File[] files, File mergedFile) {
        boolean failed = false;
        for (int i = 0; i< files.length ; i++) {
            File f = files[i];
            try (InputStream inStream = new BufferedInputStream(new FileInputStream(f))) {
                if (!failed) {
                    int fileLength = (int) f.length();
                    byte fileContent[] = new byte[fileLength];
                    inStream.read(fileContent, 0, fileLength);

                    write(fileContent, mergedFile, true);
                }
                org.apache.commons.io.FileUtils.deleteQuietly(f);
                if (i == files.length - 1) {
                    org.apache.commons.io.FileUtils.deleteQuietly(f.getParentFile());
                }
            } catch (IOException e) {
                log.error("Failed to merge file " + f.getAbsolutePath() + " to " + mergedFile.getAbsolutePath());
                failed = true;
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
        ByteSource byteSource = com.google.common.io.Files.asByteSource(file);
        HashCode hc = byteSource.hash(Hashing.sha256());
        return hc.toString();
    }

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
