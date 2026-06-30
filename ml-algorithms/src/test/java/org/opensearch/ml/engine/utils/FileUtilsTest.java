/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opensearch.ml.common.exception.MLException;

public class FileUtilsTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void testSplitFileIntoChunks() throws Exception {
        byte[] data = new byte[1017];
        new Random(42).nextBytes(data);
        File file = tempDir.newFile("large_file");
        Files.write(file.toPath(), data);

        List<String> chunkPaths = FileUtils.splitFileIntoChunks(file, tempDir.newFolder().toPath(), 325);

        int currentPosition = 0;
        for (String chunkPath : chunkPaths) {
            byte[] chunk = Files.readAllBytes(Path.of(chunkPath));
            assertArrayEquals(Arrays.copyOfRange(data, currentPosition, currentPosition + chunk.length), chunk);
            currentPosition += chunk.length;
        }
        assertEquals(data.length, currentPosition);
    }

    @Test
    public void testSplitFileIntoChunks_fileSmallerThanChunk() throws Exception {
        byte[] data = new byte[50];
        new Random(42).nextBytes(data);
        File file = tempDir.newFile("small_file");
        Files.write(file.toPath(), data);

        List<String> chunkPaths = FileUtils.splitFileIntoChunks(file, tempDir.newFolder().toPath(), 1024);

        assertEquals(1, chunkPaths.size());
        assertArrayEquals(data, Files.readAllBytes(Path.of(chunkPaths.get(0))));
    }

    @Test
    public void testWrite_createsFileWithCorrectContent() throws Exception {
        byte[] data = "hello world".getBytes();
        File dest = new File(tempDir.getRoot(), "output.bin");

        FileUtils.write(data, dest.getAbsolutePath());

        assertTrue(dest.exists());
        assertArrayEquals(data, Files.readAllBytes(dest.toPath()));
    }

    @Test
    public void testWrite_appendTrue_appendsContent() throws Exception {
        File dest = tempDir.newFile("append.bin");
        byte[] first = "hello ".getBytes();
        byte[] second = "world".getBytes();
        FileUtils.write(first, dest, false);
        FileUtils.write(second, dest, true);

        byte[] expected = new byte[first.length + second.length];
        System.arraycopy(first, 0, expected, 0, first.length);
        System.arraycopy(second, 0, expected, first.length, second.length);
        assertArrayEquals(expected, Files.readAllBytes(dest.toPath()));
    }

    @Test
    public void testMergeFiles_mergesChunksInOrder() throws Exception {
        byte[] part1 = "hello ".getBytes();
        byte[] part2 = "world".getBytes();

        // Chunks must be in a dedicated sub-folder: mergeFiles deletes the parent
        // of the last polled chunk, so the merged output must live elsewhere.
        File chunksDir = tempDir.newFolder("chunks");
        Files.write(new File(chunksDir, "chunk1").toPath(), part1);
        Files.write(new File(chunksDir, "chunk2").toPath(), part2);

        File merged = new File(tempDir.newFolder("out"), "merged.bin");
        FileUtils.mergeFiles(new ArrayDeque<>(Arrays.asList(new File(chunksDir, "chunk1"), new File(chunksDir, "chunk2"))), merged);

        byte[] expected = new byte[part1.length + part2.length];
        System.arraycopy(part1, 0, expected, 0, part1.length);
        System.arraycopy(part2, 0, expected, part1.length, part2.length);
        assertArrayEquals(expected, Files.readAllBytes(merged.toPath()));
    }

    @Test
    public void testMergeFiles_nonExistentChunkThrowsMLException() throws Exception {
        File nonExistent = new File(tempDir.newFolder("chunks_missing"), "ghost.bin");
        File merged = new File(tempDir.newFolder("out_missing"), "merged.bin");

        assertThrows(MLException.class, () -> FileUtils.mergeFiles(new ArrayDeque<>(List.of(nonExistent)), merged));
    }

    @Test
    public void testMergeFiles_failedMergeDeletesMergedFile() throws Exception {
        File chunksDir = tempDir.newFolder("chunks_fail");
        File validChunk = new File(chunksDir, "valid");
        Files.write(validChunk.toPath(), "partial".getBytes());
        File badChunk = new File(chunksDir, "missing.bin"); // does not exist

        File merged = new File(tempDir.newFolder("out_fail"), "merged.bin");
        Queue<File> files = new ArrayDeque<>(Arrays.asList(validChunk, badChunk));

        try {
            FileUtils.mergeFiles(files, merged);
            Assert.fail("Expected MLException");
        } catch (MLException e) {
            assertFalse(merged.exists());
        }
    }

    @Test
    public void testCalculateFileHash_returnsSha256Hex() throws Exception {
        File file = tempDir.newFile("hash.bin");
        Files.write(file.toPath(), "deterministic".getBytes());

        String hash = FileUtils.calculateFileHash(file);

        assertEquals("0badac3c6df445ad3aea62da1350683923aba37c685978afed96a515d12921a3", hash);
    }

    @Test
    public void testDeleteFileQuietly_deletesExistingFile() throws Exception {
        File file = tempDir.newFile("to_delete.bin");

        FileUtils.deleteFileQuietly(file.toPath());

        assertFalse(file.exists());
    }

    @Test
    public void testGetFileNames_returnsDirectChildNames() throws Exception {
        File dir = tempDir.newFolder("dir");
        new File(dir, "alpha.txt").createNewFile();
        new File(dir, "beta.txt").createNewFile();

        var names = FileUtils.getFileNames(dir.toPath());

        assertEquals(Set.of("alpha.txt", "beta.txt"), names);
    }

    @Test
    public void testGetFileNames_returnsEmptySetForNonExistentPath() {
        Path nonExistent = tempDir.getRoot().toPath().resolve("no_such_dir");

        assertTrue(FileUtils.getFileNames(nonExistent).isEmpty());
    }
}
