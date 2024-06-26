/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.ml.sdkclient.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class JsonTransformer {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @VisibleForTesting
    public static Map<String, AttributeValue> convertJsonObjectToItem(JsonNode jsonNode) {
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
                item.put(field.getKey(), AttributeValue.builder().m(convertJsonObjectToItem(field.getValue())).build());
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
                attributeValues.add(AttributeValue.builder().m(convertJsonObjectToItem(element)).build());
            } else if (element.isArray()) {
                attributeValues.add(AttributeValue.builder().l(convertJsonArrayToAttributeValueList(element)).build());
            } else {
                throw new IllegalArgumentException("Unsupported field type: " + element);
            }

        }

        return attributeValues;
    }

    public static ObjectNode convertToObjectNode(Map<String, AttributeValue> item) {
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
                    objectNode.put(key, convertToArrayNode(value.l()));
                    break;
                case M:
                    objectNode.set(key, convertToObjectNode(value.m()));
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

    public static ArrayNode convertToArrayNode(final List<AttributeValue> attributeValueList) {
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
                    arrayNode.add(convertToArrayNode(attribute.l()));
                    break;
                case M:
                    arrayNode.add(convertToObjectNode(attribute.m()));
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
}
