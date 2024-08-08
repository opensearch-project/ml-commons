/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.search.SearchModule;

public class MLUpdateConnectorRequestTests {
    private String connectorId;
    private MLCreateConnectorInput updateContent;
    private MLUpdateConnectorRequest mlUpdateConnectorRequest;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        this.connectorId = "test-connector_id";
        this.updateContent = MLCreateConnectorInput.builder().description("new description").updateConnector(true).build();
        mlUpdateConnectorRequest = MLUpdateConnectorRequest.builder().connectorId(connectorId).updateContent(updateContent).build();
    }

    @Test
    public void writeTo_Success() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlUpdateConnectorRequest.writeTo(bytesStreamOutput);
        MLUpdateConnectorRequest parsedUpdateRequest = new MLUpdateConnectorRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(connectorId, parsedUpdateRequest.getConnectorId());
        assertEquals(updateContent, parsedUpdateRequest.getUpdateContent());
    }

    @Test
    public void validate_Success() {
        assertNull(mlUpdateConnectorRequest.validate());
    }

    @Test
    public void validate_Exception_NullConnectorId() {
        MLUpdateConnectorRequest updateConnectorRequest = MLUpdateConnectorRequest.builder().build();
        Exception exception = updateConnectorRequest.validate();

        assertEquals(
            "Validation Failed: 1: ML connector id can't be null;2: Update connector content can't be null;",
            exception.getMessage()
        );
    }

    @Test
    public void parse_success() throws IOException {
        String jsonStr = "{\"version\":\"new version\",\"description\":\"new description\"}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
        parser.nextToken();
        MLUpdateConnectorRequest updateConnectorRequest = MLUpdateConnectorRequest.parse(parser, connectorId);
        assertEquals(updateConnectorRequest.getConnectorId(), connectorId);
        assertTrue(updateConnectorRequest.getUpdateContent().isUpdateConnector());
        assertEquals("new version", updateConnectorRequest.getUpdateContent().getVersion());
        assertEquals("new description", updateConnectorRequest.getUpdateContent().getDescription());
    }

    @Test
    public void fromActionRequest_Success() {
        MLUpdateConnectorRequest mlUpdateConnectorRequest = MLUpdateConnectorRequest
            .builder()
            .connectorId(connectorId)
            .updateContent(updateContent)
            .build();
        assertSame(MLUpdateConnectorRequest.fromActionRequest(mlUpdateConnectorRequest), mlUpdateConnectorRequest);
    }

    @Test
    public void fromActionRequest_Success_fromActionRequest() {
        MLUpdateConnectorRequest mlUpdateConnectorRequest = MLUpdateConnectorRequest
            .builder()
            .connectorId(connectorId)
            .updateContent(updateContent)
            .build();
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlUpdateConnectorRequest.writeTo(out);
            }
        };
        MLUpdateConnectorRequest request = MLUpdateConnectorRequest.fromActionRequest(actionRequest);
        assertNotSame(request, mlUpdateConnectorRequest);
        assertEquals(mlUpdateConnectorRequest.getConnectorId(), request.getConnectorId());
        assertEquals(mlUpdateConnectorRequest.getUpdateContent(), request.getUpdateContent());
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionRequest_IOException() {
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException();
            }
        };
        MLUpdateConnectorRequest.fromActionRequest(actionRequest);
    }
}
