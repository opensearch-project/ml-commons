package org.opensearch.ml.common.transport.deploy;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.*;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.search.SearchModule;

public class MLDeployModelRequestTest {

    private MLDeployModelRequest mlDeployModelRequest;

    @Before
    public void setUp() throws Exception {
        mlDeployModelRequest = mlDeployModelRequest
            .builder()
            .modelId("modelId")
            .modelNodeIds(new String[] { "modelNodeIds" })
            .async(true)
            .dispatchTask(true)
            .build();

    }

    @Test
    public void testValidateWithBuilder() {
        MLDeployModelRequest request = mlDeployModelRequest.builder().modelId("modelId").build();
        assertNull(request.validate());
    }

    @Test
    public void testValidateWithoutBuilder() {
        MLDeployModelRequest request = new MLDeployModelRequest("modelId", true);
        assertNull(request.validate());
    }

    @Test
    public void validate_Exception_WithNullModelId() {
        MLDeployModelRequest request = mlDeployModelRequest
            .builder()
            .modelId(null)
            .modelNodeIds(new String[] { "modelNodeIds" })
            .async(true)
            .dispatchTask(true)
            .build();
        ActionRequestValidationException exception = request.validate();
        assertEquals("Validation Failed: 1: ML model id can't be null;", exception.getMessage());
    }

    @Test
    public void writeTo_Success() throws IOException {

        MLDeployModelRequest request = mlDeployModelRequest;
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        request.writeTo(bytesStreamOutput);
        request = new MLDeployModelRequest(bytesStreamOutput.bytes().streamInput());

        assertEquals("modelId", request.getModelId());
        assertArrayEquals(new String[] { "modelNodeIds" }, request.getModelNodeIds());
        assertTrue(request.isAsync());
        assertTrue(request.isDispatchTask());
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
                throw new IOException("test");
            }
        };
        mlDeployModelRequest.fromActionRequest(actionRequest);
    }

    @Test
    public void fromActionRequest_Success_WithMLDeployModelRequest() {
        MLDeployModelRequest request = mlDeployModelRequest.builder().modelId("modelId").build();
        assertSame(mlDeployModelRequest.fromActionRequest(request), request);
    }

    @Test
    public void fromActionRequest_Success_WithNonMLDeployModelRequest() {
        MLDeployModelRequest request = mlDeployModelRequest;
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                request.writeTo(out);
            }
        };
        MLDeployModelRequest result = mlDeployModelRequest.fromActionRequest(actionRequest);
        assertNotSame(result, request);
        assertEquals(request.isAsync(), result.isAsync());
        assertEquals(request.isDispatchTask(), result.isDispatchTask());
    }

    @Test
    public void testParse() throws Exception {
        String modelId = "modelId";
        String expectedInputStr = "{\"node_ids\":[\"modelNodeIds\"]}";
        parseFromJsonString(modelId, expectedInputStr, parsedInput -> {
            assertEquals("modelId", parsedInput.getModelId());
            assertArrayEquals(new String[] { "modelNodeIds" }, parsedInput.getModelNodeIds());
            assertFalse(parsedInput.isAsync());
            assertTrue(parsedInput.isDispatchTask());
        });
    }

    @Test
    public void testParseWithInvalidField() throws Exception {
        String modelId = "modelId";
        String withInvalidFieldInputStr =
            "{\"void\":\"void\", \"dispatchTask\":\"false\", \"async\":\"true\", \"node_ids\":[\"modelNodeIds\"]}";
        parseFromJsonString(modelId, withInvalidFieldInputStr, parsedInput -> {
            assertEquals("modelId", parsedInput.getModelId());
            assertArrayEquals(new String[] { "modelNodeIds" }, parsedInput.getModelNodeIds());
            assertFalse(parsedInput.isAsync());
            assertTrue(parsedInput.isDispatchTask());
        });
    }

    private void parseFromJsonString(String modelId, String expectedInputStr, Consumer<MLDeployModelRequest> verify) throws Exception {
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                LoggingDeprecationHandler.INSTANCE,
                expectedInputStr
            );
        parser.nextToken();
        MLDeployModelRequest parsedInput = mlDeployModelRequest.parse(parser, modelId);
        verify.accept(parsedInput);
    }
}
