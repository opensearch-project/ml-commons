/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FileUtilsTest {
    private TemporaryFolder tempDir;

    @Before
    public void setUp() throws Exception {
        tempDir = new TemporaryFolder();
        tempDir.create();
    }

    @After
    public void tearDown() {
        if (tempDir != null) {
            tempDir.delete();
        }
    }

    @Test
    public void testSplitFileIntoChunks() throws Exception {
        // Write file.
        Random random = new Random();
        File file = tempDir.newFile("large_file");
        byte[] data = new byte[1017];
        random.nextBytes(data);
        Files.write(file.toPath(), data);

        // Split file into chunks.
        int chunkSize = 325;
        List<String> chunkPaths = FileUtils.splitFileIntoChunks(file, tempDir.newFolder().toPath(), chunkSize);

        // Verify.
        int currentPosition = 0;
        for (String chunkPath : chunkPaths) {
            byte[] chunk = Files.readAllBytes(Path.of(chunkPath));
            assertTrue("Chunk size", currentPosition + chunk.length <= data.length);
            Assert.assertArrayEquals(Arrays.copyOfRange(data, currentPosition, currentPosition + chunk.length), chunk);
            currentPosition += chunk.length;
        }
        assertEquals(currentPosition, data.length);
    }
}
