/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.engine.analysis;

import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

import org.opensearch.ml.engine.MLEngine;
import org.opensearch.secure_sm.AccessController;

import com.google.gson.reflect.TypeToken;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import lombok.Getter;
import lombok.Setter;

/**
 * Utility class for DJL (Deep Java Library) operations related to tokenization and model handling.
 */
public class DJLUtils {
    @Getter
    @Setter
    private static MLEngine mlEngine;

    private static <T> T withDJLContext(Callable<T> action) throws Exception {
        return AccessController.doPrivilegedChecked(() -> {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            try {
                System.setProperty("java.library.path", mlEngine.getMlCachePath().toAbsolutePath().toString());
                System.setProperty("DJL_CACHE_DIR", mlEngine.getMlCachePath().toAbsolutePath().toString());
                Thread.currentThread().setContextClassLoader(ai.djl.Model.class.getClassLoader());

                return action.call();
            } finally {
                Thread.currentThread().setContextClassLoader(contextClassLoader);
            }
        });
    }

    /**
     * Creates a new HuggingFaceTokenizer instance for the given resource path.
     * @param resourcePath The resource path of the tokenizer to create
     * @return A new HuggingFaceTokenizer instance
     * @throws RuntimeException if tokenizer initialization fails
     */
    public static HuggingFaceTokenizer buildHuggingFaceTokenizer(Path resourcePath) {
        try {
            return withDJLContext(() -> { return HuggingFaceTokenizer.newInstance(resourcePath); });
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Hugging Face tokenizer. " + e);
        }
    }

    /**
     * Fetches token weights from a specified file for a given tokenizer.
     * @param resourcePath The resource path of the tokenizer to create
     * @return A map of token to weight mappings
     * @throws RuntimeException if file fetching or parsing fails
     */
    public static Map<String, Float> fetchTokenWeights(Path resourcePath) {
        try {
            Type mapType = new TypeToken<Map<String, Float>>() {
            }.getType();
            return gson.fromJson(new InputStreamReader(Files.newInputStream(resourcePath)), mapType);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse token weights file. " + e);
        }
    }
}
