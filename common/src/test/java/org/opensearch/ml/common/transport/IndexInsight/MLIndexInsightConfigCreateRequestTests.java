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
    private String indexName;
    private String tenantId = null;

    @Test
    public void constructor() {
        indexName = "test-abc";
        MLIndexInsightConfigPutRequest mlIndexInsightConfigPutRequest = new MLIndexInsightConfigPutRequest(
            indexName,
            tenantId
        );
        assertEquals(mlIndexInsightConfigPutRequest.getContainerName(), indexName);
    }

    @Test
    public void writeTo() throws IOException {
        indexName = "test-abc";
        MLIndexInsightConfigPutRequest mlIndexInsightConfigPutRequest = new MLIndexInsightConfigPutRequest(
            indexName,
            tenantId
        );
        BytesStreamOutput output = new BytesStreamOutput();
        mlIndexInsightConfigPutRequest.writeTo(output);

        MLIndexInsightConfigPutRequest mlIndexInsightConfigPutRequest1 = new MLIndexInsightConfigPutRequest(
            output.bytes().streamInput()
        );

        assertEquals(mlIndexInsightConfigPutRequest1.getContainerName(), mlIndexInsightConfigPutRequest.getContainerName());
        assertEquals(mlIndexInsightConfigPutRequest1.getContainerName(), indexName);
    }

    @Test
    public void validate_Success() {
        indexName = "not-null";
        MLIndexInsightConfigPutRequest mlIndexInsightConfigPutRequest = new MLIndexInsightConfigPutRequest(
            indexName,
            tenantId
        );

        assertEquals(null, mlIndexInsightConfigPutRequest.validate());
    }

    @Test
    public void validate_Failure_index() {
        indexName = null;
        MLIndexInsightConfigPutRequest mlIndexInsightConfigPutRequest = new MLIndexInsightConfigPutRequest(
            indexName,
            tenantId
        );
        assertEquals(null, mlIndexInsightConfigPutRequest.getContainerName());

        ActionRequestValidationException exception = addValidationError("Index Insight's container index can't be null", null);
        mlIndexInsightConfigPutRequest.validate().equals(exception);
    }

    @Test
    public void fromActionRequest_Success() throws IOException {
        indexName = "test-abc";
        MLIndexInsightConfigPutRequest mlIndexInsightConfigPutRequest = new MLIndexInsightConfigPutRequest(
            indexName,
            tenantId
        );
        assertEquals(
            mlIndexInsightConfigPutRequest.fromActionRequest(mlIndexInsightConfigPutRequest),
                mlIndexInsightConfigPutRequest
        );
    }

    @Test
    public void fromActionRequest_Success_fromActionRequest() throws IOException {
        indexName = "test-abc";
        MLIndexInsightConfigPutRequest mlIndexInsightConfigPutRequest = new MLIndexInsightConfigPutRequest(
            indexName,
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
        assertEquals(request.getContainerName(), indexName);
    }

}
