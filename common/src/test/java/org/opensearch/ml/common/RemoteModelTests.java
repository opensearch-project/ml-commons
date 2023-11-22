/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import static org.junit.Assert.assertEquals;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.HttpConnectorTest;

public class RemoteModelTests {

    @Test
    public void toXContent_ConnectorId() throws IOException {
        MLModel mlModel = MLModel
            .builder()
            .algorithm(FunctionName.REMOTE)
            .name("test_model_name")
            .version("1.0.0")
            .modelGroupId("test_group_id")
            .description("test model")
            .connectorId("test_connector_id")
            .build();
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mlModel.toXContent(builder, EMPTY_PARAMS);
        String mlModelContent = TestHelper.xContentBuilderToString(builder);
        assertEquals(
            "{\"name\":\"test_model_name\",\"model_group_id\":\"test_group_id\",\"algorithm\":\"REMOTE\""
                + ",\"model_version\":\"1.0.0\",\"description\":\"test model\","
                + "\"connector_id\":\"test_connector_id\"}",
            mlModelContent
        );
    }

    @Test
    public void toXContent_InternalConnector() throws IOException {
        Connector connector = HttpConnectorTest.createHttpConnector();
        MLModel mlModel = MLModel
            .builder()
            .algorithm(FunctionName.REMOTE)
            .name("test_model_name")
            .version("1.0.0")
            .modelGroupId("test_group_id")
            .description("test model")
            .connector(connector)
            .build();
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mlModel.toXContent(builder, EMPTY_PARAMS);
        String mlModelContent = TestHelper.xContentBuilderToString(builder);
        assertEquals(
            "{\"name\":\"test_model_name\",\"model_group_id\":\"test_group_id\",\"algorithm\":\"REMOTE\","
                + "\"model_version\":\"1.0.0\",\"description\":\"test model\",\"connector\":{\"name\":\"test_connector_name\","
                + "\"version\":\"1\",\"description\":\"this is a test connector\",\"protocol\":\"http\","
                + "\"parameters\":{\"input\":\"test input value\"},\"credential\":{\"key\":\"test_key_value\"},"
                + "\"actions\":[{\"action_type\":\"PREDICT\",\"method\":\"POST\",\"url\":\"https://test.com\","
                + "\"headers\":{\"api_key\":\"${credential.key}\"},"
                + "\"request_body\":\"{\\\"input\\\": \\\"${parameters.input}\\\"}\","
                + "\"pre_process_function\":\"connector.pre_process.openai.embedding\","
                + "\"post_process_function\":\"connector.post_process.openai.embedding\"}],"
                + "\"backend_roles\":[\"role1\",\"role2\"],"
                + "\"access\":\"public\"}}",
            mlModelContent
        );
    }

    @Test
    public void parse_ConnectorId() throws IOException {
        MLModel mlModel = MLModel
            .builder()
            .algorithm(FunctionName.REMOTE)
            .name("test_model_name")
            .version("1.0.0")
            .modelGroupId("test_group_id")
            .description("test model")
            .connectorId("test_connector_id")
            .build();
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mlModel.toXContent(builder, EMPTY_PARAMS);
        String jsonStr = TestHelper.xContentBuilderToString(builder);
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();
        MLModel parsedModel = MLModel.parse(parser, FunctionName.REMOTE.name());
        Assert.assertNull(parsedModel.getConnector());
        Assert.assertEquals(mlModel.getConnectorId(), parsedModel.getConnectorId());
    }

    @Test
    public void parse_InternalConnector() throws IOException {
        Connector connector = HttpConnectorTest.createHttpConnector();
        MLModel mlModel = MLModel
            .builder()
            .algorithm(FunctionName.REMOTE)
            .name("test_model_name")
            .version("1.0.0")
            .modelGroupId("test_group_id")
            .description("test model")
            .connector(connector)
            .build();

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mlModel.toXContent(builder, EMPTY_PARAMS);
        String jsonStr = TestHelper.xContentBuilderToString(builder);
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();
        MLModel parsedModel = MLModel.parse(parser, FunctionName.REMOTE.name());
        Assert.assertEquals(mlModel.getConnector(), parsedModel.getConnector());
    }

    @Test
    public void readInputStream_ConnectorId() throws IOException {
        MLModel mlModel = MLModel
            .builder()
            .algorithm(FunctionName.REMOTE)
            .name("test_model_name")
            .version("1.0.0")
            .modelGroupId("test_group_id")
            .description("test model")
            .connectorId("test_connector_id")
            .build();
        readInputStream(mlModel);
    }

    @Test
    public void readInputStream_InternalConnector() throws IOException {
        Connector connector = HttpConnectorTest.createHttpConnector();
        MLModel mlModel = MLModel
            .builder()
            .algorithm(FunctionName.REMOTE)
            .name("test_model_name")
            .version("1.0.0")
            .modelGroupId("test_group_id")
            .description("test model")
            .connector(connector)
            .build();
        readInputStream(mlModel);
    }

    public void readInputStream(MLModel mlModel) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlModel.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLModel parsedMLModel = new MLModel(streamInput);
        assertEquals(mlModel.getName(), parsedMLModel.getName());
        assertEquals(mlModel.getAlgorithm(), parsedMLModel.getAlgorithm());
        assertEquals(mlModel.getVersion(), parsedMLModel.getVersion());
        assertEquals(mlModel.getContent(), parsedMLModel.getContent());
        assertEquals(mlModel.getUser(), parsedMLModel.getUser());
        assertEquals(mlModel.getConnectorId(), parsedMLModel.getConnectorId());
        assertEquals(mlModel.getConnector(), parsedMLModel.getConnector());
    }
}
