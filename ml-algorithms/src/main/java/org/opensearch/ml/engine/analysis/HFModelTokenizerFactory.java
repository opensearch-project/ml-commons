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
        private static final String ZIP_SUFFIX = ".zip";
        private static final String TOKENIZER_FILE_NAME = "tokenizer.json";
        private static final String TOKEN_WEIGHTS_FILE_NAME = "idf.json";

        final HuggingFaceTokenizer tokenizer;
        final Map<String, Float> tokenWeights;
        final String name;

        BaseTokenizerHolder(String resourcePath, String name) {
            try (InputStream is = HFModelTokenizerFactory.class.getResourceAsStream(resourcePath)) {
                if (Objects.isNull(is)) {
                    throw new RuntimeException("Invalid resource path " + resourcePath);
                }
                Files.createDirectories(DJLUtils.getMlEngine().getAnalysisRootPath());
                File tempZipFile = File.createTempFile(name, ZIP_SUFFIX, DJLUtils.getMlEngine().getAnalysisRootPath().toFile());
                Files.copy(is, tempZipFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                ZipUtils.unzip(tempZipFile, DJLUtils.getMlEngine().getAnalysisRootPath().resolve(name));
            } catch (IOException e) {
                throw new RuntimeException("Failed to extract " + name + "analyzer zip file.  " + e);
            }

            try {
                this.tokenizer = DJLUtils
                    .buildHuggingFaceTokenizer(DJLUtils.getMlEngine().getAnalysisRootPath().resolve(name).resolve(TOKENIZER_FILE_NAME));
                this.tokenWeights = DJLUtils
                    .fetchTokenWeights(DJLUtils.getMlEngine().getAnalysisRootPath().resolve(name).resolve(TOKEN_WEIGHTS_FILE_NAME));
                this.name = name;
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize tokenizer: " + name, e);
            }
        }
    }

    private static class DefaultTokenizerHolder extends BaseTokenizerHolder {
        private static final String RESOURCE_PATH = "/analysis/bert-uncased.zip";
        private static volatile DefaultTokenizerHolder INSTANCE;

        private DefaultTokenizerHolder() {
            super(RESOURCE_PATH, DEFAULT_TOKENIZER_NAME);
        }

        public static DefaultTokenizerHolder getInstance() {
            if (Objects.isNull(INSTANCE) || Objects.isNull(INSTANCE.tokenizer) || Objects.isNull(INSTANCE.tokenWeights)) {
                synchronized (DefaultTokenizerHolder.class) {
                    if (Objects.isNull(INSTANCE) || Objects.isNull(INSTANCE.tokenizer) || Objects.isNull(INSTANCE.tokenWeights)) {
                        try {
                            INSTANCE = new DefaultTokenizerHolder();
                        } catch (RuntimeException e) {
                            log.error(e.getMessage());
                        }
                    }
                }
            }
            return INSTANCE;
        }
    }

    private static class DefaultMultilingualTokenizerHolder extends BaseTokenizerHolder {
        private static final String RESOURCE_PATH = "/analysis/mbert-uncased.zip";
        private static volatile DefaultMultilingualTokenizerHolder INSTANCE;

        private DefaultMultilingualTokenizerHolder() {
            super(RESOURCE_PATH, DEFAULT_MULTILINGUAL_TOKENIZER_NAME);
        }

        public static DefaultMultilingualTokenizerHolder getInstance() {
            if (Objects.isNull(INSTANCE) || Objects.isNull(INSTANCE.tokenizer) || Objects.isNull(INSTANCE.tokenWeights)) {
                synchronized (DefaultMultilingualTokenizerHolder.class) {
                    if (Objects.isNull(INSTANCE) || Objects.isNull(INSTANCE.tokenizer) || Objects.isNull(INSTANCE.tokenWeights)) {
                        try {
                            INSTANCE = new DefaultMultilingualTokenizerHolder();
                        } catch (RuntimeException e) {
                            log.error(e.getMessage());
                        }
                    }
                }
            }
            return INSTANCE;
        }
    }

    /**
     * Creates a default tokenizer instance with predefined settings.
     * @return A new HFModelTokenizer instance with default HuggingFaceTokenizer.
     */
    public static Tokenizer createDefault() {
        return new HFModelTokenizer(
            () -> DefaultTokenizerHolder.getInstance().tokenizer,
            () -> DefaultTokenizerHolder.getInstance().tokenWeights
        );
    }

    /**
     * Creates a default multilingual tokenizer instance with predefined settings.
     * @return A new HFModelTokenizer instance with default HuggingFaceTokenizer.
     */
    public static Tokenizer createDefaultMultilingual() {
        return new HFModelTokenizer(
            () -> DefaultMultilingualTokenizerHolder.getInstance().tokenizer,
            () -> DefaultMultilingualTokenizerHolder.getInstance().tokenWeights
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
