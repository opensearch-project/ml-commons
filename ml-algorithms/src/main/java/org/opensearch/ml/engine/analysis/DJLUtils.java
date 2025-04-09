/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.engine.analysis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

import org.opensearch.ml.engine.MLEngine;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;

/**
 * Utility class for DJL (Deep Java Library) operations related to tokenization and model handling.
 */
public class DJLUtils {
    private static MLEngine mlEngine;

    /**
     * Set the DJLUtils mlEngine. Used to determine the java lib path and djl cache dir.
     * @param mlEngine The MLEngine instance created by plugin.
     */
    public static void setMLEngine(MLEngine mlEngine) {
        DJLUtils.mlEngine = mlEngine;
    }

    private static <T> T withDJLContext(Callable<T> action) throws PrivilegedActionException {
        return AccessController.doPrivileged((PrivilegedExceptionAction<T>) () -> {
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
    public static HuggingFaceTokenizer buildHuggingFaceTokenizer(String resourcePath) {
        try {
            return withDJLContext(() -> {
                InputStream is = DJLUtils.class.getResourceAsStream(resourcePath);
                if (Objects.isNull(is)) {
                    throw new IllegalArgumentException("Invalid resource path " + resourcePath);
                }
                return HuggingFaceTokenizer.newInstance(is, null);
            });
        } catch (PrivilegedActionException e) {
            throw new RuntimeException("Failed to initialize Hugging Face tokenizer. " + e);
        }
    }

    private static Map<String, Float> parseInputStreamToTokenWeights(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            Map<String, Float> tokenWeights = new HashMap<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] parts = line.split("\t");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Invalid line in token weights file: " + line);
                }
                String token = parts[0];
                float weight = Float.parseFloat(parts[1]);
                tokenWeights.put(token, weight);
            }
            return tokenWeights;
        }
    }

    /**
     * Fetches token weights from a specified file for a given tokenizer.
     * @param resourcePath The resource path of the tokenizer to create
     * @return A map of token to weight mappings
     * @throws RuntimeException if file fetching or parsing fails
     */
    public static Map<String, Float> fetchTokenWeights(String resourcePath) {
        try (InputStream is = DJLUtils.class.getResourceAsStream(resourcePath)) {
            if (Objects.isNull(is)) {
                throw new IllegalArgumentException("Invalid resource path " + resourcePath);
            }
            return parseInputStreamToTokenWeights(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse token weights file.  " + e);
        }
    }
}
