package org.opensearch.ml.common.transport.model_group;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.opensearch.ml.common.CommonValue.VERSION_2_18_0;
import static org.opensearch.ml.common.CommonValue.VERSION_2_19_0;

import java.io.IOException;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.AccessMode;

public class MLUpdateModelGroupInputTest {

    private MLUpdateModelGroupInput mlUpdateModelGroupInput;

    @Before
    public void setUp() throws Exception {

        mlUpdateModelGroupInput = mlUpdateModelGroupInput
            .builder()
            .modelGroupID("modelGroupId")
            .name("name")
            .description("description")
            .backendRoles(List.of("IT"))
            .modelAccessMode(AccessMode.RESTRICTED)
            .isAddAllBackendRoles(true)
            .build();
    }

    @Test
    public void readInputStream() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlUpdateModelGroupInput.writeTo(bytesStreamOutput);
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLUpdateModelGroupInput parsedInput = new MLUpdateModelGroupInput(streamInput);
        assertEquals(mlUpdateModelGroupInput.getName(), parsedInput.getName());
    }

    @Test
    public void readInputStream_withTenantId_Success() throws IOException {
        // Ensure tenantId is included in the test setup
        MLUpdateModelGroupInput inputWithTenantId = mlUpdateModelGroupInput.toBuilder().tenantId("tenant-1").build();

        // Serialize with a newer version that supports tenantId
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        bytesStreamOutput.setVersion(VERSION_2_19_0);
        inputWithTenantId.writeTo(bytesStreamOutput);

        // Deserialize and verify
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        streamInput.setVersion(VERSION_2_19_0);
        MLUpdateModelGroupInput parsedInput = new MLUpdateModelGroupInput(streamInput);

        assertEquals("modelGroupId", parsedInput.getModelGroupID());
        assertEquals("tenant-1", parsedInput.getTenantId());
    }

    @Test
    public void writeToAndReadFrom_withOlderVersion_TenantIdIgnored() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        bytesStreamOutput.setVersion(VERSION_2_19_0); // Serialize with newer version
        mlUpdateModelGroupInput.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        streamInput.setVersion(VERSION_2_18_0); // Deserialize with older version
        MLUpdateModelGroupInput parsedInput = new MLUpdateModelGroupInput(streamInput);

        assertEquals("modelGroupId", parsedInput.getModelGroupID());
        assertNull(parsedInput.getTenantId()); // tenantId should not be deserialized in older versions
    }

    @Test
    public void parse_withTenantId_Success() throws IOException {
        String jsonWithTenantId = "{"
                + "\"model_group_id\": \"modelGroupId\","
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
        MLUpdateModelGroupInput parsedInput = MLUpdateModelGroupInput.parse(parser);

        assertEquals("modelGroupId", parsedInput.getModelGroupID());
        assertEquals("tenant-1", parsedInput.getTenantId());
    }

    @Test
    public void parse_withoutTenantId_Success() throws IOException {
        String jsonWithoutTenantId = "{"
                + "\"model_group_id\": \"modelGroupId\","
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
        MLUpdateModelGroupInput parsedInput = MLUpdateModelGroupInput.parse(parser);

        assertEquals("modelGroupId", parsedInput.getModelGroupID());
        assertNull(parsedInput.getTenantId()); // tenantId is not provided in the JSON
    }

}
