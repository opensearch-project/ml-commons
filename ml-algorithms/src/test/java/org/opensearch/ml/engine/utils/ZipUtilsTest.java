package org.opensearch.ml.engine.utils;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class ZipUtilsTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void testEmptyZipFile() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        Path path = Paths.get("build/empty.zip");
        File file = new File(path.toUri());
        Path output = Paths.get("build/output");
        Files.createDirectories(output);
        ZipUtils.unzip(file, output);
    }

    @Test
    public void testUnzipFile() throws IOException, URISyntaxException {
        File testZipFile = new File(Objects.requireNonNull(getClass().getResource("foo.zip")).toURI());
        Path output = Paths.get("build/output");
        Files.createDirectories(output);
        ZipUtils.unzip(testZipFile, output);
        Path testOutputPath = Paths.get("build/output/foo");
        Assert.assertTrue(Files.exists(testOutputPath));
    }
}
