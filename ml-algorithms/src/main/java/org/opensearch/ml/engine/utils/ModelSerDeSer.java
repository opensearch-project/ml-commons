/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Base64;

import org.apache.commons.io.serialization.ValidatingObjectInputStream;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.engine.exceptions.ModelSerDeSerException;

import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;

@Log4j2
@UtilityClass
public class ModelSerDeSer {
    // Accept list includes OpenSearch ml plugin classes, JDK common classes and Tribuo libraries.
    public static final String[] ACCEPT_CLASS_PATTERNS = {
        "java.lang.*",
        "java.util.*",
        "java.time.*",
        "org.tribuo.*",
        "com.oracle.labs.mlrg.olcut.provenance.*",
        "com.oracle.labs.mlrg.olcut.util.*",
        "[I",
        "[Z",
        "[J",
        "[C",
        "[D",
        "[F",
        "[Ljava.lang.*",
        "[Lorg.tribuo.*",
        "[Llibsvm.*",
        "[[I",
        "[[Z",
        "[[J",
        "[[C",
        "[[D",
        "[[F",
        "[[Ljava.lang.*",
        "[[Lorg.tribuo.*",
        "[[Llibsvm.*",
        "org.opensearch.ml.*",
        "libsvm.*", };

    public static final String[] REJECT_CLASS_PATTERNS = {
        "java.util.logging.*",
        "java.util.zip.*",
        "java.util.jar.*",
        "java.util.random.*",
        "java.util.spi.*",
        "java.util.stream.*",
        "java.util.regex.*",
        "java.util.concurrent.*",
        "java.util.function.*",
        "java.util.prefs.*",
        "java.time.zone.*",
        "java.time.format.*",
        "java.time.temporal.*",
        "java.time.chrono.*", };

    public static String serializeToBase64(Object model) {
        byte[] bytes = serialize(model);
        return encodeBase64(bytes);
    }

    public static byte[] serialize(Object model) {
        try (
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)
        ) {
            objectOutputStream.writeObject(model);
            objectOutputStream.flush();
            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            throw new ModelSerDeSerException("Failed to serialize model.", e.getCause());
        }
    }

    // This method has been tested in K-means, Linear Regression, Logistic regression, Anomaly Detection and Random Cut Forest summarization
    // and passed.
    public static Object deserialize(byte[] modelBin) {
        try (
            ByteArrayInputStream inputStream = new ByteArrayInputStream(modelBin);
            ValidatingObjectInputStream validatingObjectInputStream = new ValidatingObjectInputStream(inputStream)
        ) {
            // Validate the model class type to avoid deserialization attack.
            validatingObjectInputStream.accept(ACCEPT_CLASS_PATTERNS).reject(REJECT_CLASS_PATTERNS);
            return validatingObjectInputStream.readObject();
        } catch (Throwable e) {
            log.error("Failed to deserialize model", e);
            throw new ModelSerDeSerException("Failed to deserialize model.", e.getCause());
        }
    }

    public static Object deserialize(MLModel model) {
        byte[] decodeBytes = decodeBase64(model.getContent());
        return deserialize(decodeBytes);
    }

    public static byte[] decodeBase64(String base64Str) {
        return Base64.getDecoder().decode(base64Str);
    }

    public static String encodeBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
