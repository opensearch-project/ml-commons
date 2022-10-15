/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.rcf;

import com.amazon.randomcutforest.parkservices.state.ThresholdedRandomCutForestState;
import com.amazon.randomcutforest.state.RandomCutForestState;
import io.protostuff.LinkedBuffer;
import io.protostuff.ProtostuffIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;
import lombok.experimental.UtilityClass;
import org.opensearch.ml.common.MLModel;

import java.security.AccessController;
import java.security.PrivilegedAction;

import static org.opensearch.ml.engine.utils.ModelSerDeSer.decodeBase64;

@UtilityClass
public class RCFModelSerDeSer {
    private static final int SERIALIZATION_BUFFER_BYTES = 512;
    private static final Schema<RandomCutForestState> rcfSchema =
            AccessController.doPrivileged((PrivilegedAction<Schema<RandomCutForestState>>) () ->
                    RuntimeSchema.getSchema(RandomCutForestState.class));
    private static final Schema<ThresholdedRandomCutForestState> trcfSchema =
            AccessController.doPrivileged((PrivilegedAction<Schema<ThresholdedRandomCutForestState>>) () ->
                    RuntimeSchema.getSchema(ThresholdedRandomCutForestState.class));

    public static byte[] serializeRCF(RandomCutForestState model) {
        return serialize(model, rcfSchema);
    }

    public static byte[] serializeTRCF(ThresholdedRandomCutForestState model) {
        return serialize(model, trcfSchema);
    }

    public static RandomCutForestState deserializeRCF(MLModel model) {
        return deserializeRCF(decodeBase64(model.getContent()));
    }

    public static RandomCutForestState deserializeRCF(byte[] bytes) {
        return deserialize(bytes, rcfSchema);
    }

    public static ThresholdedRandomCutForestState deserializeTRCF(MLModel model) {
        return deserializeTRCF(decodeBase64(model.getContent()));
    }

    public static ThresholdedRandomCutForestState deserializeTRCF(byte[] bytes) {
        return deserialize(bytes, trcfSchema);
    }

    private static <T> byte[] serialize(T model, Schema<T> schema) {
        LinkedBuffer buffer = LinkedBuffer.allocate(SERIALIZATION_BUFFER_BYTES);
        byte[] bytes = AccessController.doPrivileged((PrivilegedAction<byte[]>) () -> ProtostuffIOUtil.toByteArray(model, schema, buffer));
        return bytes;
    }

    private static <T> T deserialize(byte[] bytes, Schema<T> schema) {
        T model = schema.newMessage();
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            ProtostuffIOUtil.mergeFrom(bytes, model, schema);
            return null;
        });
        return model;
    }
}
