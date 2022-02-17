/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.utils;

import lombok.experimental.UtilityClass;
import org.opensearch.ml.engine.exceptions.ModelSerDeSerException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

@UtilityClass
public class ModelSerDeSer {
    // Welcome list includes OpenSearch ml plugin classes, JDK common classes and Tribuo libraries.
    public static final String[] ACCEPT_CLASS_PATTERNS = {
        "java.lang.*",
        "java.util.*",
        "java.time.*",
        "org.opensearch.ml.*",
        "*org.tribuo.*",
        "libsvm.*",
        "com.oracle.labs.*",
        "[*",
        "com.amazon.randomcutforest.*"
    };

    public static byte[] serialize(Object model) {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(model);
            objectOutputStream.flush();
            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            throw new ModelSerDeSerException("Failed to serialize model.", e.getCause());
        }
    }

    public static Object deserialize(byte[] modelBin) {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(modelBin))) {
            return objectInputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new ModelSerDeSerException("Failed to deserialize model.", e.getCause());
        }
    }
}
