/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output.model;

import lombok.Data;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.ml.common.model.MLResultDataType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Data
public class ModelTensor implements Writeable, ToXContentObject {
    private String name;
    private Number[] data;
    private long[] shape;
    private MLResultDataType dataType;
    private ByteBuffer byteBuffer;

    public ModelTensor(String name, Number[] data, long[] shape, MLResultDataType dataType, ByteBuffer byteBuffer) {
        if (this.data != null && (dataType == null || dataType == MLResultDataType.UNKNOWN)) {
            throw new IllegalArgumentException("data type is null");
        }
        this.name = name;
        this.data = data;
        this.shape = shape;
        this.dataType = dataType;
        this.byteBuffer = byteBuffer;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (name != null) {
            builder.field("name", name);
        }
        if (dataType != null) {
            builder.field("data_type", dataType);
        }
        if (shape != null) {
            builder.field("shape", shape);
        }
        if (data != null) {
            builder.field("data", data);
        }
        if (byteBuffer != null) {
            builder.startObject("byte_buffer");
            builder.field("array", byteBuffer.array());
            builder.field("order", byteBuffer.order().toString());
            builder.endObject();
        }
        builder.endObject();
        return builder;
    }

    public ModelTensor(StreamInput in) throws IOException {
        this.name = in.readOptionalString();
        if (in.readBoolean()) {
            this.dataType = in.readEnum(MLResultDataType.class);
        }
        if (in.readBoolean()) {
            this.shape = in.readLongArray();
        }
        if (in.readBoolean()) {
            int size = in.readInt();
            data = new Number[size];
            if (dataType.isFloating()) {
                for (int i=0; i<size; i++) {
                    data[i] = in.readFloat();
                }
            } else if (dataType.isInteger() || dataType.isBoolean()) {
                for (int i=0; i<size; i++) {
                    data[i] = in.readInt();
                }
            } else {
                data = null;
            }
        }
        if (in.readBoolean()) {
            String orderName = in.readString();
            ByteOrder byteOrder = null;
            if (ByteOrder.BIG_ENDIAN.toString().equals(orderName)) {
                byteOrder = ByteOrder.BIG_ENDIAN;
            } else if (ByteOrder.LITTLE_ENDIAN.toString().equals(orderName)) {
                byteOrder = ByteOrder.LITTLE_ENDIAN;
            } else {
                throw new IllegalArgumentException("wrong byte order");
            }
            byte[] bytes = in.readByteArray();
            this.byteBuffer = ByteBuffer.wrap(bytes);
            this.byteBuffer.order(byteOrder);
        }

    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(name);
        if (dataType != null) {
            out.writeBoolean(true);
            out.writeEnum(dataType);
        } else {
            out.writeBoolean(false);
        }
        if (shape != null) {
            out.writeBoolean(true);
            out.writeLongArray(shape);
        } else {
            out.writeBoolean(false);
        }
        if (data != null && dataType != null && dataType != MLResultDataType.UNKNOWN) {
            out.writeBoolean(true);
            out.writeInt(data.length);
            if (dataType.isFloating()) {
                for (Number n : data) {
                    out.writeFloat(n.floatValue());
                }
            } else if (dataType.isInteger() || dataType.isBoolean()) {
                for (Number n : data) {
                    out.writeInt(n.intValue());
                }
            }
        } else {
            out.writeBoolean(false);
        }
        if (byteBuffer != null && byteBuffer.hasArray()) {
            out.writeBoolean(true);
            out.writeString(byteBuffer.order().toString());
            out.writeByteArray(byteBuffer.array());
        } else {
            out.writeBoolean(false);
        }
    }
}
