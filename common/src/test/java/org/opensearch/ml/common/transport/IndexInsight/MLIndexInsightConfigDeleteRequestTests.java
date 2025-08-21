/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.IndexInsight;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightContainerDeleteRequest;

public class MLIndexInsightConfigDeleteRequestTests {
    String tenantId = "test_tenant";

    @Test
    public void constructor() {
        MLIndexInsightContainerDeleteRequest mlIndexInsightContainerDeleteRequest = new MLIndexInsightContainerDeleteRequest(tenantId);
        assertEquals(mlIndexInsightContainerDeleteRequest.getTenantId(), tenantId);
    }

    @Test
    public void writeTo() throws IOException {
        MLIndexInsightContainerDeleteRequest mlIndexInsightContainerDeleteRequest = new MLIndexInsightContainerDeleteRequest(tenantId);
        BytesStreamOutput output = new BytesStreamOutput();
        mlIndexInsightContainerDeleteRequest.writeTo(output);

        MLIndexInsightContainerDeleteRequest mlIndexInsightContainerDeleteRequest1 = new MLIndexInsightContainerDeleteRequest(
            output.bytes().streamInput()
        );

        assertEquals(mlIndexInsightContainerDeleteRequest1.getTenantId(), mlIndexInsightContainerDeleteRequest.getTenantId());
        assertEquals(mlIndexInsightContainerDeleteRequest1.getTenantId(), tenantId);
    }

    @Test
    public void validate_Success() {
        MLIndexInsightContainerDeleteRequest mlIndexInsightContainerDeleteRequest = new MLIndexInsightContainerDeleteRequest(tenantId);

        assertEquals(null, mlIndexInsightContainerDeleteRequest.validate());
    }

    @Test
    public void fromActionRequest_Success() throws IOException {
        MLIndexInsightContainerDeleteRequest mlIndexInsightContainerDeleteRequest = new MLIndexInsightContainerDeleteRequest(tenantId);
        assertEquals(
            mlIndexInsightContainerDeleteRequest.fromActionRequest(mlIndexInsightContainerDeleteRequest),
            mlIndexInsightContainerDeleteRequest
        );
    }

    @Test
    public void fromActionRequest_Success_fromActionRequest() throws IOException {
        MLIndexInsightContainerDeleteRequest mlIndexInsightContainerDeleteRequest = new MLIndexInsightContainerDeleteRequest(tenantId);

        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlIndexInsightContainerDeleteRequest.writeTo(out);
            }
        };
        MLIndexInsightContainerDeleteRequest request = mlIndexInsightContainerDeleteRequest.fromActionRequest(actionRequest);
        assertEquals(request.getTenantId(), tenantId);
    }
}
