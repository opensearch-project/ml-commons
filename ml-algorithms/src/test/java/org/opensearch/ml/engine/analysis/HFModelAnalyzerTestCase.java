/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.engine.analysis;

import java.nio.file.Path;
import java.util.UUID;

import org.junit.Before;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;

public abstract class HFModelAnalyzerTestCase {
    private Path mlCachePath;
    private MLEngine mlEngine;
    private Encryptor encryptor;

    @Before
    public void setUp() throws Exception {
        mlCachePath = Path.of("/tmp/ml_cache" + UUID.randomUUID());
        encryptor = new EncryptorImpl(null, "m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=");
        mlEngine = new MLEngine(mlCachePath, encryptor);
        DJLUtils.setMLEngine(mlEngine);
    }
}
