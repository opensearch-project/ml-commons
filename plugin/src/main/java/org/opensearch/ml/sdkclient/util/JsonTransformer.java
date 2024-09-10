/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.ml.sdkclient.util;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializer;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;

import jakarta.json.Json;
import jakarta.json.JsonReader;
import jakarta.json.stream.JsonGenerator;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class JsonTransformer {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static Map<String, AttributeValue> convertJsonObjectToDDBAttributeMap(JsonNode jsonNode) {
        Map<String, AttributeValue> item = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (field.getValue().isTextual()) {
                item.put(field.getKey(), AttributeValue.builder().s(field.getValue().asText()).build());
            } else if (field.getValue().isNumber()) {
                item.put(field.getKey(), AttributeValue.builder().n(field.getValue().asText()).build());
            } else if (field.getValue().isBoolean()) {
                item.put(field.getKey(), AttributeValue.builder().bool(field.getValue().asBoolean()).build());
            } else if (field.getValue().isNull()) {
                item.put(field.getKey(), AttributeValue.builder().nul(true).build());
            } else if (field.getValue().isObject()) {
                item.put(field.getKey(), AttributeValue.builder().m(convertJsonObjectToDDBAttributeMap(field.getValue())).build());
            } else if (field.getValue().isArray()) {
                item.put(field.getKey(), AttributeValue.builder().l(convertJsonArrayToAttributeValueList(field.getValue())).build());
            } else {
                throw new IllegalArgumentException("Unsupported field type: " + field.getValue());
            }
        }

        return item;
    }

    @VisibleForTesting
    public static List<AttributeValue> convertJsonArrayToAttributeValueList(JsonNode jsonArray) {
        List<AttributeValue> attributeValues = new ArrayList<>();

        for (JsonNode element : jsonArray) {
            if (element.isTextual()) {
                attributeValues.add(AttributeValue.builder().s(element.asText()).build());
            } else if (element.isNumber()) {
                attributeValues.add(AttributeValue.builder().n(element.asText()).build());
            } else if (element.isBoolean()) {
                attributeValues.add(AttributeValue.builder().bool(element.asBoolean()).build());
            } else if (element.isNull()) {
                attributeValues.add(AttributeValue.builder().nul(true).build());
            } else if (element.isObject()) {
                attributeValues.add(AttributeValue.builder().m(convertJsonObjectToDDBAttributeMap(element)).build());
            } else if (element.isArray()) {
                attributeValues.add(AttributeValue.builder().l(convertJsonArrayToAttributeValueList(element)).build());
            } else {
                throw new IllegalArgumentException("Unsupported field type: " + element);
            }

        }

        return attributeValues;
    }

    public static ObjectNode convertDDBAttributeValueMapToObjectNode(Map<String, AttributeValue> item) {
        ObjectNode objectNode = OBJECT_MAPPER.createObjectNode();

        item.forEach((key, value) -> {
            switch (value.type()) {
                case S:
                    objectNode.put(key, value.s());
                    break;
                case N:
                    objectNode.put(key, value.n());
                    break;
                case BOOL:
                    objectNode.put(key, value.bool());
                    break;
                case L:
                    objectNode.put(key, convertAttributeValueListToArrayNode(value.l()));
                    break;
                case M:
                    objectNode.set(key, convertDDBAttributeValueMapToObjectNode(value.m()));
                    break;
                case NUL:
                    objectNode.putNull(key);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported AttributeValue type: " + value.type());
            }
        });

        return objectNode;

    }

    public static ArrayNode convertAttributeValueListToArrayNode(final List<AttributeValue> attributeValueList) {
        ArrayNode arrayNode = OBJECT_MAPPER.createArrayNode();
        attributeValueList.forEach(attribute -> {
            switch (attribute.type()) {
                case S:
                    arrayNode.add(attribute.s());
                    break;
                case N:
                    arrayNode.add(attribute.n());
                    break;
                case BOOL:
                    arrayNode.add(attribute.bool());
                    break;
                case L:
                    arrayNode.add(convertAttributeValueListToArrayNode(attribute.l()));
                    break;
                case M:
                    arrayNode.add(convertDDBAttributeValueMapToObjectNode(attribute.m()));
                    break;
                case NUL:
                    arrayNode.add((JsonNode) null);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported AttributeValue type: " + attribute.type());
            }
        });
        return arrayNode;

    }

    public static class XContentObjectJsonpSerializer implements JsonpSerializer<Object> {
        @Override
        public void serialize(Object obj, JsonGenerator generator, JsonpMapper mapper) {
            if (obj instanceof ToXContentObject) {
                serialize((ToXContentObject) obj, generator);
            } else {
                throw new IllegalArgumentException(
                    "This method requires an object of type ToXContentObject, actual type is " + obj.getClass().getName()
                );
            }
        }

        private void serialize(ToXContentObject obj, JsonGenerator generator) {
            try (XContentBuilder builder = XContentFactory.jsonBuilder()) {
                obj.toXContent(builder, ToXContent.EMPTY_PARAMS);
                serializeString(builder.toString(), generator);
            } catch (IOException e) {
                throw new OpenSearchStatusException("Error parsing XContentObject", RestStatus.BAD_REQUEST);
            }
        }

        private void serializeString(String json, JsonGenerator generator) {
            try (JsonReader jsonReader = Json.createReader(new StringReader(json))) {
                generator.write(jsonReader.readObject());
            }
        }
    }
}
