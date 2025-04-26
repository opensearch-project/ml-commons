/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.engine.analysis;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.junit.Before;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.engine.utils.ZipUtils;

public abstract class HFModelAnalyzerTestCase {
    static protected Path mlCachePath = Path.of("/tmp/ml_cache" + UUID.randomUUID());
    static protected Encryptor encryptor = new EncryptorImpl(null, "m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=");
    static protected MLEngine mlEngine = new MLEngine(mlCachePath, encryptor);

    @Before
    public void setUp() throws Exception {
        DJLUtils.setMlEngine(mlEngine);

        if (Files.notExists(mlEngine.getAnalysisRootPath().resolve("test"))) {
            Files.createDirectories(mlEngine.getAnalysisRootPath());
            File tempZipFile = File.createTempFile("test", ".zip", DJLUtils.getMlEngine().getAnalysisRootPath().toFile());
            Files
                .copy(
                    HFModelTokenizerFactory.class.getResourceAsStream("/analysis/bert-uncased.zip"),
                    tempZipFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                );
            ZipUtils.unzip(tempZipFile, DJLUtils.getMlEngine().getAnalysisRootPath().resolve("test"));
        }
    }
}
