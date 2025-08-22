/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.IndexInsight;

import static org.junit.Assert.assertEquals;
import static org.opensearch.action.ValidateActions.addValidationError;

import java.io.IOException;

import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightConfigPutRequest;

public class MLIndexInsightConfigCreateRequestTests {
    private Boolean isEnable;
    private String tenantId = null;

    @Test
    public void constructor() {
        isEnable = true;
        MLIndexInsightConfigPutRequest mlIndexInsightConfigPutRequest = new MLIndexInsightConfigPutRequest(
                isEnable,
            tenantId
        );
        assertEquals(mlIndexInsightConfigPutRequest.getIsEnable(), isEnable);
    }

    @Test
    public void writeTo() throws IOException {
        isEnable = true;
        MLIndexInsightConfigPutRequest mlIndexInsightConfigPutRequest = new MLIndexInsightConfigPutRequest(
            isEnable,
            tenantId
        );
        BytesStreamOutput output = new BytesStreamOutput();
        mlIndexInsightConfigPutRequest.writeTo(output);

        MLIndexInsightConfigPutRequest mlIndexInsightConfigPutRequest1 = new MLIndexInsightConfigPutRequest(
            output.bytes().streamInput()
        );

        assertEquals(mlIndexInsightConfigPutRequest1.getIsEnable(), mlIndexInsightConfigPutRequest.getIsEnable());
        assertEquals(mlIndexInsightConfigPutRequest1.getTenantId(), tenantId);
    }

    @Test
    public void validate_Success() {
        isEnable = false;
        MLIndexInsightConfigPutRequest mlIndexInsightConfigPutRequest = new MLIndexInsightConfigPutRequest(
                isEnable,
            tenantId
        );

        assertEquals(null, mlIndexInsightConfigPutRequest.validate());
    }

    @Test
    public void validate_Failure_index() {
        isEnable = null;
        MLIndexInsightConfigPutRequest mlIndexInsightConfigPutRequest = new MLIndexInsightConfigPutRequest(
                isEnable,
            tenantId
        );
        assertEquals(null, mlIndexInsightConfigPutRequest.getIsEnable());

        ActionRequestValidationException exception = addValidationError("Index Insight's container index can't be null", null);
        mlIndexInsightConfigPutRequest.validate().equals(exception);
    }

    @Test
    public void fromActionRequest_Success() throws IOException {
        isEnable = true;
        MLIndexInsightConfigPutRequest mlIndexInsightConfigPutRequest = new MLIndexInsightConfigPutRequest(
                isEnable,
            tenantId
        );
        assertEquals(
            mlIndexInsightConfigPutRequest.fromActionRequest(mlIndexInsightConfigPutRequest),
                mlIndexInsightConfigPutRequest
        );
    }

    @Test
    public void fromActionRequest_Success_fromActionRequest() throws IOException {
        isEnable = true;
        MLIndexInsightConfigPutRequest mlIndexInsightConfigPutRequest = new MLIndexInsightConfigPutRequest(
                isEnable,
            tenantId
        );

        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlIndexInsightConfigPutRequest.writeTo(out);
            }
        };
        MLIndexInsightConfigPutRequest request = mlIndexInsightConfigPutRequest.fromActionRequest(actionRequest);
        assertEquals(request.getIsEnable(), isEnable);
    }

}
