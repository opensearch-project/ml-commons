/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output.model;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.utils.GsonUtil;

@Data
public class ModelTensor implements Writeable, ToXContentObject {

    public static final String NAME_FIELD = "name";
    public static final String DATA_TYPE_FIELD = "data_type";
    public static final String SHAPE_FIELD = "shape";
    public static final String DATA_FIELD = "data";
    public static final String BYTE_BUFFER_FIELD = "byte_buffer";
    public static final String BYTE_BUFFER_ARRAY_FIELD = "array";
    public static final String BYTE_BUFFER_ORDER_FIELD = "order";
    public static final String RESULT_FIELD = "result";
    public static final String DATA_AS_MAP_FIELD = "dataAsMap";

    private String name;
    private Number[] data;
    private long[] shape;
    private MLResultDataType dataType;
    private ByteBuffer byteBuffer;// whole result in bytes
    private String result;// whole result in string
    private Map<String, ?> dataAsMap;// whole result in Map

    @Builder
    public ModelTensor(String name, Number[] data, long[] shape, MLResultDataType dataType, ByteBuffer byteBuffer, String result, Map<String, ?> dataAsMap) {
        if (data != null && (dataType == null || dataType == MLResultDataType.UNKNOWN)) {
            throw new IllegalArgumentException("data type is null");
        }
        this.name = name;
        this.data = data;
        this.shape = shape;
        this.dataType = dataType;
        this.byteBuffer = byteBuffer;
        this.result = result;
        this.dataAsMap = dataAsMap;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        if (name != null) {
            builder.field(NAME_FIELD, name);
        }
        if (dataType != null) {
            builder.field(DATA_TYPE_FIELD, dataType);
        }
        if (shape != null) {
            builder.field(SHAPE_FIELD, shape);
        }
        if (data != null) {
            builder.field(DATA_FIELD, data);
        }
        if (byteBuffer != null) {
            builder.startObject(BYTE_BUFFER_FIELD);
            builder.field(BYTE_BUFFER_ARRAY_FIELD, byteBuffer.array());
            builder.field(BYTE_BUFFER_ORDER_FIELD, byteBuffer.order().toString());
            builder.endObject();
        }
        if (result != null) {
            builder.field(RESULT_FIELD, result);
        }
        if (dataAsMap != null) {
            builder.field(DATA_AS_MAP_FIELD, dataAsMap);
        }
        builder.endObject();
        return builder;
    }

    public static ModelTensor parser(XContentParser parser) throws IOException {
        String name = null;
        List<Object> dataList = null;
        Number[] data = null;
        long[] shape = null;
        MLResultDataType dataType = null;
        ByteBuffer byteBuffer = null;// whole result in bytes
        String result = null;// whole result in string
        Map<String, ?> dataAsMap = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case NAME_FIELD:
                    name = parser.text();
                    break;
                case DATA_FIELD:
                    dataList = parser.list();
                    break;
                case DATA_TYPE_FIELD:
                    dataType = MLResultDataType.valueOf(parser.text());
                    break;
                case SHAPE_FIELD:
                    List<Long> shapeList = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        shapeList.add(parser.longValue());
                    }
                    shape = new long[shapeList.size()];
                    for (int i = 0; i < shapeList.size(); i++) {
                        shape[i] = shapeList.get(i);
                    }
                    break;
                case RESULT_FIELD:
                    result = parser.text();
                    break;
                case DATA_AS_MAP_FIELD:
                    dataAsMap = parser.map();
                    break;
                case BYTE_BUFFER_FIELD:
                    byte[] bytes = null;
                    ByteOrder order = null;
                    ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                        String byteBufferFieldName = parser.currentName();
                        parser.nextToken();
                        switch (byteBufferFieldName) {
                            case BYTE_BUFFER_ARRAY_FIELD:
                                bytes = parser.binaryValue();
                                break;
                            case BYTE_BUFFER_ORDER_FIELD:
                                String orderName = parser.text();
                                if (ByteOrder.LITTLE_ENDIAN.toString().equals(orderName)) {
                                    order = ByteOrder.LITTLE_ENDIAN;
                                } else if (ByteOrder.BIG_ENDIAN.toString().equals(orderName)) {
                                    order = ByteOrder.BIG_ENDIAN;
                                }
                                break;
                        }
                        if (bytes != null) {
                            byteBuffer = ByteBuffer.wrap(bytes);
                            if (order != null) {
                                byteBuffer.order(order);
                            }
                        }
                    }
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        if (dataType != null && dataList != null && dataList.size() > 0) {
            data = new Number[dataList.size()];
            for (int i = 0; i < dataList.size(); i++) {
                data[i] = (Number) dataList.get(i);
            }
        }
        return ModelTensor.builder()
                .name(name)
                .shape(shape)
                .dataType(dataType)
                .data(data)
                .result(result)
                .dataAsMap(dataAsMap)
                .build();
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
                for (int i = 0; i < size; i++) {
                    data[i] = in.readFloat();
                }
            } else if (dataType.isInteger() || dataType.isBoolean()) {
                for (int i = 0; i < size; i++) {
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
        this.result = in.readOptionalString();
        if (in.readBoolean()) {
            String mapStr = in.readString();
            this.dataAsMap = GsonUtil.fromJson(mapStr, Map.class);
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
        out.writeOptionalString(result);
        if (dataAsMap != null) {
            out.writeBoolean(true);
            try {
                AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                    out.writeString(GsonUtil.toJson(dataAsMap));
                    return null;
                });
            } catch (PrivilegedActionException e) {
                throw new RuntimeException(e);
            }
        } else {
            out.writeBoolean(false);
        }
    }
}
