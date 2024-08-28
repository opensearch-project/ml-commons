/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static java.util.Collections.emptyMap;
import static org.opensearch.ml.utils.TestHelper.ML_ROLE;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.opensearch.Version;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodeRole;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.MLTask;
import org.opensearch.test.OpenSearchTestCase;

import com.fasterxml.jackson.core.JsonParseException;

public class MLNodeUtilsTests extends OpenSearchTestCase {

    public void testIsMLNode() {
        Set<DiscoveryNodeRole> roleSet = new HashSet<>();
        roleSet.add(DiscoveryNodeRole.DATA_ROLE);
        roleSet.add(DiscoveryNodeRole.INGEST_ROLE);
        DiscoveryNode normalNode = new DiscoveryNode("Normal node", buildNewFakeTransportAddress(), emptyMap(), roleSet, Version.CURRENT);
        Assert.assertFalse(MLNodeUtils.isMLNode(normalNode));

        roleSet.add(ML_ROLE);
        DiscoveryNode mlNode = new DiscoveryNode("ML node", buildNewFakeTransportAddress(), emptyMap(), roleSet, Version.CURRENT);
        Assert.assertTrue(MLNodeUtils.isMLNode(mlNode));
    }

    public void testCreateXContentParserFromRegistry() throws IOException {
        MLTask mlTask = MLTask.builder().taskId("taskId").modelId("modelId").build();
        XContentBuilder content = mlTask.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        NamedXContentRegistry namedXContentRegistry = NamedXContentRegistry.EMPTY;
        XContentParser xContentParser = MLNodeUtils.createXContentParserFromRegistry(namedXContentRegistry, bytesReference);
        xContentParser.nextToken();
        MLTask parsedMLTask = MLTask.parse(xContentParser);
        assertEquals(mlTask, parsedMLTask);
    }

    @Test
    public void testValidateSchema() throws IOException {
        String schema = "{"
            + "\"type\": \"object\","
            + "\"properties\": {"
            + "    \"key1\": {\"type\": \"string\"},"
            + "    \"key2\": {\"type\": \"integer\"}"
            + "}"
            + "}";
        String json = "{\"key1\": \"foo\", \"key2\": 123}";
        MLNodeUtils.validateSchema(schema, json);
    }

    @Test
    public void testProcessRemoteInferenceInputDataSetParametersValueNoParameters() throws IOException {
        String json = "{\"key1\":\"foo\",\"key2\":123,\"key3\":true}";
        String processedJson = MLNodeUtils.processRemoteInferenceInputDataSetParametersValue(json);
        assertEquals(json, processedJson);
    }

    @Test
    public void testProcessRemoteInferenceInputDataSetInvalidJson() {
        String json = "{\"key1\":\"foo\",\"key2\":123,\"key3\":true,\"parameters\":{\"a\"}}";
        assertThrows(JsonParseException.class, () -> MLNodeUtils.processRemoteInferenceInputDataSetParametersValue(json));
    }

    @Test
    public void testProcessRemoteInferenceInputDataSetEmptyParameters() throws IOException {
        String json = "{\"key1\":\"foo\",\"key2\":123,\"key3\":true,\"parameters\":{}}";
        String processedJson = MLNodeUtils.processRemoteInferenceInputDataSetParametersValue(json);
        assertEquals(json, processedJson);
    }

    @Test
    public void testProcessRemoteInferenceInputDataSetParametersValueParametersWrongType() throws IOException {
        String json = "{\"key1\":\"foo\",\"key2\":123,\"key3\":true,\"parameters\":[\"Hello\",\"world\"]}";
        String processedJson = MLNodeUtils.processRemoteInferenceInputDataSetParametersValue(json);
        assertEquals(json, processedJson);
    }

    @Test
    public void testProcessRemoteInferenceInputDataSetParametersValueWithParametersProcessArray() throws IOException {
        String json = "{\"key1\":\"foo\",\"key2\":123,\"key3\":true,\"parameters\":{\"texts\":\"[\\\"Hello\\\",\\\"world\\\"]\"}}";
        String expectedJson = "{\"key1\":\"foo\",\"key2\":123,\"key3\":true,\"parameters\":{\"texts\":[\"Hello\",\"world\"]}}";
        String processedJson = MLNodeUtils.processRemoteInferenceInputDataSetParametersValue(json);
        assertEquals(expectedJson, processedJson);
    }

    @Test
    public void testProcessRemoteInferenceInputDataSetParametersValueWithParametersProcessObject() throws IOException {
        String json =
            "{\"key1\":\"foo\",\"key2\":123,\"key3\":true,\"parameters\":{\"messages\":\"{\\\"role\\\":\\\"system\\\",\\\"foo\\\":\\\"{\\\\\\\"a\\\\\\\": \\\\\\\"b\\\\\\\"}\\\",\\\"content\\\":{\\\"a\\\":\\\"b\\\"}}\"}}}";
        String expectedJson =
            "{\"key1\":\"foo\",\"key2\":123,\"key3\":true,\"parameters\":{\"messages\":{\"role\":\"system\",\"foo\":\"{\\\"a\\\": \\\"b\\\"}\",\"content\":{\"a\":\"b\"}}}}";
        String processedJson = MLNodeUtils.processRemoteInferenceInputDataSetParametersValue(json);
        assertEquals(expectedJson, processedJson);
    }

    @Test
    public void testProcessRemoteInferenceInputDataSetParametersValueWithParametersNoProcess() throws IOException {
        String json = "{\"key1\":\"foo\",\"key2\":123,\"key3\":true,\"parameters\":{\"key1\":\"foo\",\"key2\":123,\"key3\":true}}";
        String processedJson = MLNodeUtils.processRemoteInferenceInputDataSetParametersValue(json);
        assertEquals(json, processedJson);
    }

    @Test
    public void testProcessRemoteInferenceInputDataSetParametersValueWithParametersInvalidJson() throws IOException {
        String json =
            "{\"key1\":\"foo\",\"key2\":123,\"key3\":true,\"parameters\":{\"key1\":\"foo\",\"key2\":123,\"key3\":true,\"texts\":\"[\\\"Hello\\\",\\\"world\\\"\"}}";
        String processedJson = MLNodeUtils.processRemoteInferenceInputDataSetParametersValue(json);
        assertEquals(json, processedJson);
    }
}
