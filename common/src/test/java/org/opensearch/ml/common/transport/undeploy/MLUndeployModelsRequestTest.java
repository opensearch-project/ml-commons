package org.opensearch.ml.common.transport.undeploy;

import static org.junit.Assert.*;
import static org.opensearch.ml.common.CommonValue.VERSION_2_18_0;
import static org.opensearch.ml.common.CommonValue.VERSION_2_19_0;

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
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.search.SearchModule;

public class MLUndeployModelsRequestTest {

    private MLUndeployModelsRequest mlUndeployModelsRequest;

    @Before
    public void setUp() {
        mlUndeployModelsRequest = MLUndeployModelsRequest
            .builder()
            .modelIds(new String[] { "model1", "model2" })
            .nodeIds(new String[] { "node1", "node2" })
            .async(true)
            .dispatchTask(true)
            .tenantId("tenant1")
            .build();
    }

    @Test
    public void testValidate() {
        MLUndeployModelsRequest request = MLUndeployModelsRequest.builder().modelIds(new String[] { "model1" }).build();
        assertNull(request.validate());
    }

    @Test
    public void testStreamInputVersionBefore_2_19_0() throws IOException {
        BytesStreamOutput out = new BytesStreamOutput();
        out.setVersion(VERSION_2_18_0);
        mlUndeployModelsRequest.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        in.setVersion(VERSION_2_18_0);
        MLUndeployModelsRequest request = new MLUndeployModelsRequest(in);

        assertArrayEquals(mlUndeployModelsRequest.getModelIds(), request.getModelIds());
        assertArrayEquals(mlUndeployModelsRequest.getNodeIds(), request.getNodeIds());
        assertEquals(mlUndeployModelsRequest.isAsync(), request.isAsync());
        assertEquals(mlUndeployModelsRequest.isDispatchTask(), request.isDispatchTask());
        assertNull(request.getTenantId());
    }

    @Test
    public void testStreamInputVersionAfter_2_19_0() throws IOException {
        BytesStreamOutput out = new BytesStreamOutput();
        out.setVersion(VERSION_2_19_0);
        mlUndeployModelsRequest.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        in.setVersion(VERSION_2_19_0);
        MLUndeployModelsRequest request = new MLUndeployModelsRequest(in);

        assertArrayEquals(mlUndeployModelsRequest.getModelIds(), request.getModelIds());
        assertArrayEquals(mlUndeployModelsRequest.getNodeIds(), request.getNodeIds());
        assertEquals(mlUndeployModelsRequest.isAsync(), request.isAsync());
        assertEquals(mlUndeployModelsRequest.isDispatchTask(), request.isDispatchTask());
        assertEquals(mlUndeployModelsRequest.getTenantId(), request.getTenantId());
    }

    @Test
    public void testWriteToWithNullFields() throws IOException {
        MLUndeployModelsRequest request = MLUndeployModelsRequest
            .builder()
            .modelIds(null)
            .nodeIds(null)
            .async(true)
            .dispatchTask(true)
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        out.setVersion(VERSION_2_19_0);
        request.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        in.setVersion(VERSION_2_19_0);
        MLUndeployModelsRequest result = new MLUndeployModelsRequest(in);

        assertNull(result.getModelIds());
        assertNull(result.getNodeIds());
        assertEquals(request.isAsync(), result.isAsync());
        assertEquals(request.isDispatchTask(), result.isDispatchTask());
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
        MLUndeployModelsRequest.fromActionRequest(actionRequest);
    }

    @Test
    public void fromActionRequest_Success_WithMLUndeployModelsRequest() {
        MLUndeployModelsRequest request = MLUndeployModelsRequest.builder().modelIds(new String[] { "model1" }).build();
        assertSame(MLUndeployModelsRequest.fromActionRequest(request), request);
    }

    @Test
    public void testParse() throws Exception {
        String expectedInputStr = "{\"model_ids\":[\"model1\"],\"node_ids\":[\"node1\"]}";
        parseFromJsonString(expectedInputStr, parsedInput -> {
            assertArrayEquals(new String[] { "model1" }, parsedInput.getModelIds());
            assertArrayEquals(new String[] { "node1" }, parsedInput.getNodeIds());
            assertFalse(parsedInput.isAsync());
            assertTrue(parsedInput.isDispatchTask());
        });
    }

    @Test
    public void testParseWithInvalidField() throws Exception {
        String withInvalidFieldInputStr = "{\"invalid_field\":\"void\",\"model_ids\":[\"model1\"],\"node_ids\":[\"node1\"]}";
        parseFromJsonString(withInvalidFieldInputStr, parsedInput -> {
            assertArrayEquals(new String[] { "model1" }, parsedInput.getModelIds());
            assertArrayEquals(new String[] { "node1" }, parsedInput.getNodeIds());
        });
    }

    private void parseFromJsonString(String expectedInputStr, Consumer<MLUndeployModelsRequest> verify) throws Exception {
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                LoggingDeprecationHandler.INSTANCE,
                expectedInputStr
            );
        parser.nextToken();
        MLUndeployModelsRequest parsedInput = MLUndeployModelsRequest.parse(parser, null);
        verify.accept(parsedInput);
    }
}
