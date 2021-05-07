/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
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
            ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
            res = objectInputStream.readObject();
            objectInputStream.close();
            inputStream.close();
        } catch (IOException | ClassNotFoundException e) {
            throw new ModelSerDeSerException("Failed to deserialize model.", e.getCause());
        }

        return res;
    }
}
