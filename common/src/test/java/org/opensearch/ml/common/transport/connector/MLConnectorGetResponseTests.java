/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.HttpConnectorTest;

public class MLConnectorGetResponseTests {
    Connector connector;

    @Before
    public void setUp() {
        connector = HttpConnectorTest.createHttpConnector();
    }

    @Test
    public void writeTo_Success() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        MLConnectorGetResponse response = MLConnectorGetResponse.builder().mlConnector(connector).build();
        response.writeTo(bytesStreamOutput);
        MLConnectorGetResponse parsedResponse = new MLConnectorGetResponse(bytesStreamOutput.bytes().streamInput());
        assertNotEquals(response, parsedResponse);
        assertNotSame(response.mlConnector, parsedResponse.mlConnector);
        assertEquals(response.mlConnector, parsedResponse.mlConnector);
        assertEquals(response.mlConnector.getName(), parsedResponse.mlConnector.getName());
        assertEquals(response.mlConnector.getAccess(), parsedResponse.mlConnector.getAccess());
        assertEquals(response.mlConnector.getProtocol(), parsedResponse.mlConnector.getProtocol());
        assertEquals(response.mlConnector.getDecryptedHeaders(), parsedResponse.mlConnector.getDecryptedHeaders());
        assertEquals(response.mlConnector.getBackendRoles(), parsedResponse.mlConnector.getBackendRoles());
        assertEquals(response.mlConnector.getActions(), parsedResponse.mlConnector.getActions());
        assertEquals(response.mlConnector.getParameters(), parsedResponse.mlConnector.getParameters());
    }

    @Test
    public void toXContentTest() throws IOException {
        MLConnectorGetResponse mlConnectorGetResponse = MLConnectorGetResponse.builder().mlConnector(connector).build();
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        mlConnectorGetResponse.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = builder.toString();
        assertEquals(
            "{\"name\":\"test_connector_name\","
                + "\"version\":\"1\",\"description\":\"this is a test connector\",\"protocol\":\"http\","
                + "\"parameters\":{\"input\":\"test input value\"},\"credential\":{\"key\":\"test_key_value\"},"
                + "\"actions\":[{\"action_type\":\"PREDICT\",\"method\":\"POST\",\"url\":\"https://test.com\","
                + "\"headers\":{\"api_key\":\"${credential.key}\"},"
                + "\"request_body\":\"{\\\"input\\\": \\\"${parameters.input}\\\"}\","
                + "\"pre_process_function\":\"connector.pre_process.openai.embedding\","
                + "\"post_process_function\":\"connector.post_process.openai.embedding\"}],"
                + "\"backend_roles\":[\"role1\",\"role2\"],"
                + "\"access\":\"public\"}",
            jsonStr
        );
    }

    @Test
    public void fromActionResponseWithMLConnectorGetResponse_Success() {
        MLConnectorGetResponse mlConnectorGetResponse = MLConnectorGetResponse.builder().mlConnector(connector).build();
        MLConnectorGetResponse mlConnectorGetResponseFromActionResponse = MLConnectorGetResponse.fromActionResponse(mlConnectorGetResponse);
        assertSame(mlConnectorGetResponse, mlConnectorGetResponseFromActionResponse);
        assertEquals(mlConnectorGetResponse.mlConnector, mlConnectorGetResponseFromActionResponse.mlConnector);
    }

    @Test
    public void fromActionResponse_Success() {
        MLConnectorGetResponse mlConnectorGetResponse = MLConnectorGetResponse.builder().mlConnector(connector).build();
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlConnectorGetResponse.writeTo(out);
            }
        };
        MLConnectorGetResponse mlConnectorGetResponseFromActionResponse = MLConnectorGetResponse.fromActionResponse(actionResponse);
        assertNotSame(mlConnectorGetResponse, mlConnectorGetResponseFromActionResponse);
        assertEquals(mlConnectorGetResponse.mlConnector, mlConnectorGetResponseFromActionResponse.mlConnector);
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionResponse_IOException() {
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException();
            }
        };
        MLConnectorGetResponse.fromActionResponse(actionResponse);
    }
}
