/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_ROLE_NAME;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Function;

import org.opensearch.OpenSearchParseException;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.breaker.CircuitBreaker;
import org.opensearch.core.common.breaker.CircuitBreakingException;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.breaker.MLCircuitBreakerService;
import org.opensearch.ml.breaker.ThresholdCircuitBreaker;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.networknt.schema.ValidationMessage;

import lombok.experimental.UtilityClass;

@UtilityClass
public class MLNodeUtils {
    public boolean isMLNode(DiscoveryNode node) {
        return node.getRoles().stream().anyMatch(role -> role.roleName().equalsIgnoreCase(ML_ROLE_NAME));
    }

    public static XContentParser createXContentParserFromRegistry(NamedXContentRegistry xContentRegistry, BytesReference bytesReference)
        throws IOException {
        return XContentHelper.createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, bytesReference, XContentType.JSON);
    }

    public static void parseArrayField(XContentParser parser, Set<String> set) throws IOException {
        parseField(parser, set, null, String.class);
    }

    public static <T> void parseField(XContentParser parser, Set<T> set, Function<String, T> function, Class<T> clazz) throws IOException {
        ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
            String value = parser.text();
            if (function != null) {
                set.add(function.apply(value));
            } else {
                if (clazz.isInstance(value)) {
                    set.add(clazz.cast(value));
                }
            }
        }
    }

    public static void validateSchema(String schemaString, String instanceString) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        // parse the schema JSON as string
        JsonNode schemaNode = mapper.readTree(schemaString);
        JsonSchema schema = JsonSchemaFactory.getInstance(VersionFlag.V202012).getSchema(schemaNode);

        // JSON data to validate
        JsonNode jsonNode = mapper.readTree(instanceString);

        // Validate JSON node against the schema
        Set<ValidationMessage> errors = schema.validate(jsonNode);
        if (!errors.isEmpty()) {
            throw new OpenSearchParseException(
                "Validation failed: "
                    + Arrays.toString(errors.toArray(new ValidationMessage[0]))
                    + " for instance: "
                    + instanceString
                    + " with schema: "
                    + schemaString
            );
        }
    }

    /**
     * This method processes the input JSON string and replaces the string values of the parameters with JSON objects if the string is a valid JSON, unless the schema defines the value as a string.
     * @param inputJson The input JSON string
     * @param schemaJson The schema matching the input JSON string
     * @return The processed JSON string
     */
    public static String processRemoteInferenceInputDataSetParametersValue(String inputJson, String schemaJson) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(inputJson);
        JsonNode schemaNode = mapper.readTree(schemaJson);

        // Get the schema properties for parameters if they exist
        JsonNode parametersSchema = schemaNode.path("properties").path("parameters").path("properties");

        if (rootNode.has("parameters") && rootNode.get("parameters").isObject()) {
            ObjectNode parametersNode = (ObjectNode) rootNode.get("parameters");

            parametersNode.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();

                if (value.isTextual() && !isStringTypeInSchema(parametersSchema, key)) {
                    try {
                        JsonNode parsedValue = mapper.readTree(value.asText());
                        parametersNode.set(key, parsedValue);
                    } catch (IOException e) {
                        // If parsing fails, keep it as is
                        parametersNode.set(key, value);
                    }
                }
            });
        }
        return mapper.writeValueAsString(rootNode);
    }

    private static boolean isStringTypeInSchema(JsonNode schema, String fieldName) {
        JsonNode typeNode = schema.path(fieldName).path("type");
        return typeNode.isTextual() && typeNode.asText().equals("string");
    }

    public static void checkOpenCircuitBreaker(MLCircuitBreakerService mlCircuitBreakerService, MLStats mlStats) {
        ThresholdCircuitBreaker openCircuitBreaker = mlCircuitBreakerService.checkOpenCB();
        if (openCircuitBreaker != null) {
            mlStats.getStat(MLNodeLevelStat.ML_CIRCUIT_BREAKER_TRIGGER_COUNT).increment();
            throw new CircuitBreakingException(
                openCircuitBreaker.getName() + " is open, please check your resources!",
                CircuitBreaker.Durability.TRANSIENT
            );
        }
    }
}
