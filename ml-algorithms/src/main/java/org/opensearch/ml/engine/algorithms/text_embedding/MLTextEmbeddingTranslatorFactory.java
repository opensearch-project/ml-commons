/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.text_embedding;

import ai.djl.Model;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorFactory;
import ai.djl.util.Pair;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MLTextEmbeddingTranslatorFactory implements TranslatorFactory {

    private static final Set<Pair<Type, Type>> SUPPORTED_TYPES = new HashSet<>();

    static {
        SUPPORTED_TYPES.add(new Pair<>(String.class, float[].class));
        SUPPORTED_TYPES.add(new Pair<>(Input.class, Output.class));
    }

    /** {@inheritDoc} */
    @Override
    public Set<Pair<Type, Type>> getSupportedTypes() {
        return SUPPORTED_TYPES;
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("unchecked")
    public <I, O> Translator<I, O> newInstance(
            Class<I> input, Class<O> output, Model model, Map<String, ?> arguments)
            throws TranslateException {
        Path modelPath = model.getModelPath();
        try {
            HuggingFaceTokenizer tokenizer =
                    HuggingFaceTokenizer.builder(arguments)
                            .optTokenizerPath(modelPath)
                            .optManager(model.getNDManager())
                            .build();
            MLTextEmbeddingTranslator translator =
                    MLTextEmbeddingTranslator.builder(tokenizer, arguments).build();
            if (input == String.class && output == float[].class) {
                return (Translator<I, O>) translator;
            } else if (input == Input.class && output == Output.class) {
                return (Translator<I, O>) new MLTextEmbeddingServingTranslator(translator);
            }
            throw new IllegalArgumentException("Unsupported input/output types.");
        } catch (IOException e) {
            throw new TranslateException("Failed to load tokenizer.", e);
        }
    }
}
