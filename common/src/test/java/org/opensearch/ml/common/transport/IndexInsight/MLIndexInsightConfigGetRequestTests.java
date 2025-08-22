package org.opensearch.ml.common.transport.IndexInsight;

import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.indexInsight.MLIndexInsightType;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightConfigGetRequest;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetRequest;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.opensearch.action.ValidateActions.addValidationError;
import static org.opensearch.ml.common.indexInsight.MLIndexInsightType.FIELD_DESCRIPTION;

public class MLIndexInsightConfigGetRequestTests {
    private String tenantId = null;

    @Test
    public void constructor() {
        MLIndexInsightConfigGetRequest mlIndexInsightConfigGetRequest = new MLIndexInsightConfigGetRequest(tenantId);
        assertEquals(mlIndexInsightConfigGetRequest.getTenantId(), tenantId);
    }

    @Test
    public void writeTo() throws IOException {
        MLIndexInsightConfigGetRequest mlIndexInsightConfigGetRequest = new MLIndexInsightConfigGetRequest(tenantId);
        BytesStreamOutput output = new BytesStreamOutput();
        mlIndexInsightConfigGetRequest.writeTo(output);

        MLIndexInsightConfigGetRequest mlIndexInsightConfigGetRequest1 = new MLIndexInsightConfigGetRequest(output.bytes().streamInput());

        assertEquals(mlIndexInsightConfigGetRequest1.getTenantId(), mlIndexInsightConfigGetRequest.getTenantId());
    }

    @Test
    public void writeTo_WithTenantId() throws IOException {
        String tenantId = "demo_id";
        MLIndexInsightConfigGetRequest mlIndexInsightConfigGetRequest = new MLIndexInsightConfigGetRequest(tenantId);
        BytesStreamOutput output = new BytesStreamOutput();
        mlIndexInsightConfigGetRequest.writeTo(output);

        MLIndexInsightConfigGetRequest mlIndexInsightConfigGetRequest1 = new MLIndexInsightConfigGetRequest(output.bytes().streamInput());

        assertEquals(mlIndexInsightConfigGetRequest.getTenantId(), mlIndexInsightConfigGetRequest1.getTenantId());
    }

    @Test
    public void validate_Success() {
        MLIndexInsightConfigGetRequest mlIndexInsightConfigGetRequest = new MLIndexInsightConfigGetRequest(tenantId);
        assertEquals(null, mlIndexInsightConfigGetRequest.validate());
    }

    @Test
    public void fromActionRequest_Success() throws IOException {
        MLIndexInsightConfigGetRequest mlIndexInsightConfigGetRequest = new MLIndexInsightConfigGetRequest(tenantId);
        assertEquals(mlIndexInsightConfigGetRequest.fromActionRequest(mlIndexInsightConfigGetRequest), mlIndexInsightConfigGetRequest);
    }

    @Test
    public void fromActionRequest_Success_fromActionRequest() throws IOException {
        MLIndexInsightConfigGetRequest mlIndexInsightConfigGetRequest = new MLIndexInsightConfigGetRequest(tenantId);

        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlIndexInsightConfigGetRequest.writeTo(out);
            }
        };
        MLIndexInsightConfigGetRequest request = mlIndexInsightConfigGetRequest.fromActionRequest(actionRequest);
        assertEquals(request.getTenantId(), tenantId);
    }
}
