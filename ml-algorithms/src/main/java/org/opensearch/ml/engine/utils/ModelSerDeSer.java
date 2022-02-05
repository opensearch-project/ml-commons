/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.utils;

import lombok.experimental.UtilityClass;
import org.apache.commons.io.serialization.ValidatingObjectInputStream;
import org.opensearch.ml.engine.exceptions.ModelSerDeSerException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
        "[*"
    };

    public static byte[] serialize(Object model) {
        byte[] res = new byte[0];
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(model);
            objectOutputStream.flush();
            res = byteArrayOutputStream.toByteArray();
            objectOutputStream.close();
            byteArrayOutputStream.close();
        } catch (IOException e) {
            throw new ModelSerDeSerException("Failed to serialize model.", e.getCause());
        }

        return res;
    }

    public static Object deserialize(byte[] modelBin) {
        Object res;
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(modelBin);
            ValidatingObjectInputStream validatingObjectInputStream = new ValidatingObjectInputStream(inputStream);

            // Validate the model class type to avoid deserialization attack.
            validatingObjectInputStream.accept(ACCEPT_CLASS_PATTERNS);

            res = validatingObjectInputStream.readObject();
            validatingObjectInputStream.close();
            inputStream.close();
        } catch (IOException | ClassNotFoundException e) {
            throw new ModelSerDeSerException("Failed to deserialize model.", e.getCause());
        }

        return res;
    }
}
