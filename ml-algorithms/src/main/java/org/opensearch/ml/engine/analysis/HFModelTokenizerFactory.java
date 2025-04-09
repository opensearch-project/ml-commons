/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.engine.analysis;

import java.util.Map;

import org.apache.lucene.analysis.Tokenizer;
import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.analysis.AbstractTokenizerFactory;

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
        final HuggingFaceTokenizer TOKENIZER;
        final Map<String, Float> TOKEN_WEIGHTS;
        final String NAME;

        BaseTokenizerHolder(String tokenizerPath, String tokenWeightsPath, String name) {
            try {
                this.TOKENIZER = DJLUtils.buildHuggingFaceTokenizer(tokenizerPath);
                this.TOKEN_WEIGHTS = DJLUtils.fetchTokenWeights(tokenWeightsPath);
                this.NAME = name;
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize tokenizer: " + name, e);
            }
        }
    }

    private static class DefaultTokenizerHolder extends BaseTokenizerHolder {
        private static final String TOKENIZER_PATH = "/analysis/tokenizer_en.json";
        private static final String TOKEN_WEIGHTS_PATH = "/analysis/token_weights_en.txt";

        private static final DefaultTokenizerHolder INSTANCE = new DefaultTokenizerHolder();

        private DefaultTokenizerHolder() {
            super(TOKENIZER_PATH, TOKEN_WEIGHTS_PATH, DEFAULT_TOKENIZER_NAME);
        }
    }

    private static class DefaultMultilingualTokenizerHolder extends BaseTokenizerHolder {
        private static final String TOKENIZER_PATH = "/analysis/tokenizer_multi.json";
        private static final String TOKEN_WEIGHTS_PATH = "/analysis/token_weights_multi.txt";

        private static final DefaultMultilingualTokenizerHolder INSTANCE = new DefaultMultilingualTokenizerHolder();

        private DefaultMultilingualTokenizerHolder() {
            super(TOKENIZER_PATH, TOKEN_WEIGHTS_PATH, DEFAULT_MULTILINGUAL_TOKENIZER_NAME);
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
