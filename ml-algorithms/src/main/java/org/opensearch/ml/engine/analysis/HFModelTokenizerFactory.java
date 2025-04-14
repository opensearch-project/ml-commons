/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.engine.analysis;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;

import org.apache.lucene.analysis.Tokenizer;
import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.analysis.AbstractTokenizerFactory;
import org.opensearch.ml.engine.utils.ZipUtils;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import lombok.extern.log4j.Log4j2;

/**
 * Factory class for creating HFModelTokenizer instances.
 * Handles the initialization and configuration of tokenizers based on index settings.
 */
@Log4j2
public class HFModelTokenizerFactory extends AbstractTokenizerFactory {
    public static final String DEFAULT_TOKENIZER_NAME = "bert-uncased";
    public static final String DEFAULT_MULTILINGUAL_TOKENIZER_NAME = "mbert-uncased";

    /**
     * Atomically loads the HF tokenizer in a lazy fashion once the outer class accesses the static final set the first time.;
     */
    private static abstract class BaseTokenizerHolder {
        private static final String ZIP_PREFIX = ".zip";
        private static final String TOKENIZER_FILE_NAME = "tokenizer.json";
        private static final String TOKEN_WEIGHTS_FILE_NAME = "idf.json";

        final HuggingFaceTokenizer TOKENIZER;
        final Map<String, Float> TOKEN_WEIGHTS;
        final String NAME;

        BaseTokenizerHolder(String resourcePath, String name) {
            try (InputStream is = HFModelTokenizerFactory.class.getResourceAsStream(resourcePath)) {
                if (Objects.isNull(is)) {
                    throw new IllegalArgumentException("Invalid resource path " + resourcePath);
                }
                Files.createDirectories(DJLUtils.getMlEngine().getAnalysisRootPath());
                File tempZipFile = File.createTempFile(name, ZIP_PREFIX, DJLUtils.getMlEngine().getAnalysisRootPath().toFile());
                Files.copy(is, tempZipFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                ZipUtils.unzip(tempZipFile, DJLUtils.getMlEngine().getAnalysisRootPath().resolve(name));
            } catch (IOException e) {
                throw new RuntimeException("Failed to extract " + name + "analyzer zip file.  " + e);
            }

            try {
                this.TOKENIZER = DJLUtils
                    .buildHuggingFaceTokenizer(DJLUtils.getMlEngine().getAnalysisRootPath().resolve(name).resolve(TOKENIZER_FILE_NAME));
                this.TOKEN_WEIGHTS = DJLUtils
                    .fetchTokenWeights(DJLUtils.getMlEngine().getAnalysisRootPath().resolve(name).resolve(TOKEN_WEIGHTS_FILE_NAME));
                this.NAME = name;
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize tokenizer: " + name, e);
            }
        }
    }

    private static class DefaultTokenizerHolder extends BaseTokenizerHolder {
        private static final String RESOURCE_PATH = "/analysis/bert-uncased.zip";

        private static final DefaultTokenizerHolder INSTANCE = new DefaultTokenizerHolder();

        private DefaultTokenizerHolder() {
            super(RESOURCE_PATH, DEFAULT_TOKENIZER_NAME);
        }
    }

    private static class DefaultMultilingualTokenizerHolder extends BaseTokenizerHolder {
        private static final String RESOURCE_PATH = "/analysis/mbert-uncased.zip";

        private static final DefaultMultilingualTokenizerHolder INSTANCE = new DefaultMultilingualTokenizerHolder();

        private DefaultMultilingualTokenizerHolder() {
            super(RESOURCE_PATH, DEFAULT_MULTILINGUAL_TOKENIZER_NAME);
        }
    }

    /**
     * Creates a default tokenizer instance with predefined settings.
     * @return A new HFModelTokenizer instance with default HuggingFaceTokenizer.
     */
    public static Tokenizer createDefault() {
        return new HFModelTokenizer(() -> DefaultTokenizerHolder.INSTANCE.TOKENIZER, () -> DefaultTokenizerHolder.INSTANCE.TOKEN_WEIGHTS);
    }

    /**
     * Creates a default multilingual tokenizer instance with predefined settings.
     * @return A new HFModelTokenizer instance with default HuggingFaceTokenizer.
     */
    public static Tokenizer createDefaultMultilingual() {
        return new HFModelTokenizer(
            () -> DefaultMultilingualTokenizerHolder.INSTANCE.TOKENIZER,
            () -> DefaultMultilingualTokenizerHolder.INSTANCE.TOKEN_WEIGHTS
        );
    }

    public HFModelTokenizerFactory(IndexSettings indexSettings, Environment environment, String name, Settings settings) {
        // For custom tokenizer, the factory is created during IndexModule.newIndexService
        // And can be accessed via indexService.getIndexAnalyzers()
        super(indexSettings, settings, name);
    }

    @Override
    public Tokenizer create() {
        // the create method will be called for every single analyze request
        return createDefault();
    }
}
