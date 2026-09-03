package org.opensearch.ml.common.transport.model_group;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.CommonValue.VERSION_2_18_0;
import static org.opensearch.ml.common.CommonValue.VERSION_2_19_0;

import java.io.IOException;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.AccessMode;

public class MLRegisterModelGroupInputTest {

    private MLRegisterModelGroupInput mlRegisterModelGroupInput;

    @Before
    public void setUp() throws Exception {

        mlRegisterModelGroupInput = mlRegisterModelGroupInput
            .builder()
            .name("name")
            .description("description")
            .backendRoles(Arrays.asList("IT"))
            .modelAccessMode(AccessMode.RESTRICTED)
            .isAddAllBackendRoles(true)
            .build();
    }

    @Test
    public void readInputStream() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlRegisterModelGroupInput.writeTo(bytesStreamOutput);
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLRegisterModelGroupInput parsedInput = new MLRegisterModelGroupInput(streamInput);
        assertEquals(mlRegisterModelGroupInput.getName(), parsedInput.getName());
    }

    @Test
    public void writeToAndReadFrom_withTenantId_Success() throws IOException {
        MLRegisterModelGroupInput input = MLRegisterModelGroupInput
            .builder()
            .name("name")
            .description("description")
            .backendRoles(Arrays.asList("IT"))
            .modelAccessMode(AccessMode.RESTRICTED)
            .isAddAllBackendRoles(true)
            .tenantId("tenant-1")
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        out.setVersion(VERSION_2_19_0); // Serialize with newer version
        input.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        in.setVersion(VERSION_2_19_0); // Deserialize with the same version
        MLRegisterModelGroupInput parsedInput = new MLRegisterModelGroupInput(in);

        assertEquals("name", parsedInput.getName());
        assertEquals("tenant-1", parsedInput.getTenantId());
    }

    @Test
    public void writeToAndReadFrom_withOlderVersion_TenantIdIgnored() throws IOException {
        MLRegisterModelGroupInput input = MLRegisterModelGroupInput
            .builder()
            .name("name")
            .description("description")
            .backendRoles(Arrays.asList("IT"))
            .modelAccessMode(AccessMode.RESTRICTED)
            .isAddAllBackendRoles(true)
            .tenantId("tenant-1")
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        out.setVersion(VERSION_2_19_0); // Serialize with newer version
        input.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        in.setVersion(VERSION_2_18_0); // Deserialize with older version
        MLRegisterModelGroupInput parsedInput = new MLRegisterModelGroupInput(in);

        assertEquals("name", parsedInput.getName());
        assertNull(parsedInput.getTenantId()); // tenantId should not be deserialized in older versions
    }

    @Test
    public void parse_withTenantId_Success() throws IOException {
        String jsonWithTenantId = "{"
            + "\"name\": \"name\","
            + "\"description\": \"description\","
            + "\"backend_roles\": [\"IT\"],"
            + "\"access_mode\": \"restricted\","
            + "\"add_all_backend_roles\": true,"
            + "\"tenant_id\": \"tenant-1\""
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.THROW_UNSUPPORTED_OPERATION, jsonWithTenantId);

        parser.nextToken(); // Start parsing
        MLRegisterModelGroupInput parsedInput = MLRegisterModelGroupInput.parse(parser);

        assertEquals("name", parsedInput.getName());
        assertEquals("tenant-1", parsedInput.getTenantId());
    }

    @Test
    public void parse_withoutTenantId_Success() throws IOException {
        String jsonWithoutTenantId = "{"
            + "\"name\": \"name\","
            + "\"description\": \"description\","
            + "\"backend_roles\": [\"IT\"],"
            + "\"access_mode\": \"restricted\","
            + "\"add_all_backend_roles\": true"
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.THROW_UNSUPPORTED_OPERATION, jsonWithoutTenantId);

        parser.nextToken(); // Start parsing
        MLRegisterModelGroupInput parsedInput = MLRegisterModelGroupInput.parse(parser);

        assertEquals("name", parsedInput.getName());
        assertNull(parsedInput.getTenantId()); // tenantId is not provided in the JSON
    }

    @Test
    public void parse_withCustomModelGroupId() throws IOException {
        String json = "{\"name\":\"name\",\"model_group_id\":\"my-model-group\"}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.THROW_UNSUPPORTED_OPERATION, json);
        parser.nextToken();
        MLRegisterModelGroupInput parsedInput = MLRegisterModelGroupInput.parse(parser);
        assertEquals("my-model-group", parsedInput.getModelGroupId());
    }

    @Test
    public void toXContent_WithCustomModelGroupId() throws Exception {
        MLRegisterModelGroupInput input = MLRegisterModelGroupInput.builder().name("name").modelGroupId("my-model-group").build();
        XContentBuilder builder = XContentFactory.jsonBuilder();
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertTrue(builder.toString().contains("\"model_group_id\":\"my-model-group\""));
    }

    @Test
    public void writeToAndReadFrom_withCustomModelGroupId() throws IOException {
        MLRegisterModelGroupInput input = MLRegisterModelGroupInput.builder().name("name").modelGroupId("my-model-group").build();
        BytesStreamOutput out = new BytesStreamOutput();
        out.setVersion(org.opensearch.ml.common.CommonValue.VERSION_3_9_0);
        input.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        in.setVersion(org.opensearch.ml.common.CommonValue.VERSION_3_9_0);
        MLRegisterModelGroupInput parsed = new MLRegisterModelGroupInput(in);
        assertEquals("my-model-group", parsed.getModelGroupId());
    }

}
