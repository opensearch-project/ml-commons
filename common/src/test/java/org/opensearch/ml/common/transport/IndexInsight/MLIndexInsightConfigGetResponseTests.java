package org.opensearch.ml.common.transport.IndexInsight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
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
import org.opensearch.ml.common.indexInsight.IndexInsightConfig;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightConfigGetResponse;

public class MLIndexInsightConfigGetResponseTests {
    IndexInsightConfig indexInsightConfig;

    @Before
    public void setUp() {
        indexInsightConfig = new IndexInsightConfig(true, "demo-tenant-id");
    }

    @Test
    public void writeToSuccess() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        MLIndexInsightConfigGetResponse response = MLIndexInsightConfigGetResponse.builder().indexInsightConfig(indexInsightConfig).build();
        response.writeTo(bytesStreamOutput);
        MLIndexInsightConfigGetResponse parsedResponse = new MLIndexInsightConfigGetResponse(bytesStreamOutput.bytes().streamInput());
        assertNotEquals(response, parsedResponse);
        assertNotSame(response.getIndexInsightConfig(), parsedResponse.getIndexInsightConfig());
        assertEquals(response.getIndexInsightConfig().getIsEnable(), parsedResponse.getIndexInsightConfig().getIsEnable());
        assertEquals(response.getIndexInsightConfig().getTenantId(), parsedResponse.getIndexInsightConfig().getTenantId());
    }

    @Test
    public void toXContentTest() throws IOException {
        MLIndexInsightConfigGetResponse mlIndexInsightConfigGetResponse = MLIndexInsightConfigGetResponse
            .builder()
            .indexInsightConfig(indexInsightConfig)
            .build();
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        mlIndexInsightConfigGetResponse.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = builder.toString();

        String expectedControllerResponse = "{\"is_enable\":true,\"tenant_id\":\"demo-tenant-id\"}";
        assertEquals(expectedControllerResponse, jsonStr);
    }

    @Test
    public void fromActionResponseWithMLConnectorGetResponseSuccess() {
        MLIndexInsightConfigGetResponse mlIndexInsightConfigGetResponse = MLIndexInsightConfigGetResponse
            .builder()
            .indexInsightConfig(indexInsightConfig)
            .build();
        MLIndexInsightConfigGetResponse mlIndexInsightConfigGetResponse1 = MLIndexInsightConfigGetResponse
            .fromActionResponse(mlIndexInsightConfigGetResponse);
        assertSame(mlIndexInsightConfigGetResponse, mlIndexInsightConfigGetResponse1);
        assertEquals(mlIndexInsightConfigGetResponse.getIndexInsightConfig(), mlIndexInsightConfigGetResponse1.getIndexInsightConfig());
    }

    @Test
    public void fromActionResponseSuccess() {
        MLIndexInsightConfigGetResponse mlIndexInsightConfigGetResponse = MLIndexInsightConfigGetResponse
            .builder()
            .indexInsightConfig(indexInsightConfig)
            .build();
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlIndexInsightConfigGetResponse.writeTo(out);
            }
        };
        MLIndexInsightConfigGetResponse mlIndexInsightConfigGetResponse1 = MLIndexInsightConfigGetResponse
            .fromActionResponse(actionResponse);
        assertNotSame(mlIndexInsightConfigGetResponse, mlIndexInsightConfigGetResponse1);
        assertNotSame(mlIndexInsightConfigGetResponse.getIndexInsightConfig(), mlIndexInsightConfigGetResponse1.getIndexInsightConfig());
        assertEquals(
            mlIndexInsightConfigGetResponse.getIndexInsightConfig().getIsEnable(),
            mlIndexInsightConfigGetResponse1.getIndexInsightConfig().getIsEnable()
        );
        assertEquals(
            mlIndexInsightConfigGetResponse.getIndexInsightConfig().getTenantId(),
            mlIndexInsightConfigGetResponse1.getIndexInsightConfig().getTenantId()
        );
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionResponseIOException() {
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException();
            }
        };
        MLIndexInsightConfigGetResponse.fromActionResponse(actionResponse);
    }

    @Test
    public void testNullConfig() {
        MLIndexInsightConfigGetResponse response = MLIndexInsightConfigGetResponse.builder().build();
        assertNull(response.getIndexInsightConfig());
    }

}
