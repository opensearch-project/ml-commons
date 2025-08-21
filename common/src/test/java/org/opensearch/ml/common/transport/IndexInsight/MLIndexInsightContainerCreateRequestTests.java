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
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightContainerCreateRequest;

public class MLIndexInsightContainerCreateRequestTests {
    private String indexName;
    private String tenantId = null;

    @Test
    public void constructor() {
        indexName = "test-abc";
        MLIndexInsightContainerCreateRequest mlIndexInsightContainerCreateRequest = new MLIndexInsightContainerCreateRequest(
            indexName,
            tenantId
        );
        assertEquals(mlIndexInsightContainerCreateRequest.getContainerName(), indexName);
    }

    @Test
    public void writeTo() throws IOException {
        indexName = "test-abc";
        MLIndexInsightContainerCreateRequest mlIndexInsightContainerCreateRequest = new MLIndexInsightContainerCreateRequest(
            indexName,
            tenantId
        );
        BytesStreamOutput output = new BytesStreamOutput();
        mlIndexInsightContainerCreateRequest.writeTo(output);

        MLIndexInsightContainerCreateRequest mlIndexInsightContainerCreateRequest1 = new MLIndexInsightContainerCreateRequest(
            output.bytes().streamInput()
        );

        assertEquals(mlIndexInsightContainerCreateRequest1.getContainerName(), mlIndexInsightContainerCreateRequest.getContainerName());
        assertEquals(mlIndexInsightContainerCreateRequest1.getContainerName(), indexName);
    }

    @Test
    public void validate_Success() {
        indexName = "not-null";
        MLIndexInsightContainerCreateRequest mlIndexInsightContainerCreateRequest = new MLIndexInsightContainerCreateRequest(
            indexName,
            tenantId
        );

        assertEquals(null, mlIndexInsightContainerCreateRequest.validate());
    }

    @Test
    public void validate_Failure_index() {
        indexName = null;
        MLIndexInsightContainerCreateRequest mlIndexInsightContainerCreateRequest = new MLIndexInsightContainerCreateRequest(
            indexName,
            tenantId
        );
        assertEquals(null, mlIndexInsightContainerCreateRequest.getContainerName());

        ActionRequestValidationException exception = addValidationError("Index Insight's container index can't be null", null);
        mlIndexInsightContainerCreateRequest.validate().equals(exception);
    }

    @Test
    public void fromActionRequest_Success() throws IOException {
        indexName = "test-abc";
        MLIndexInsightContainerCreateRequest mlIndexInsightContainerCreateRequest = new MLIndexInsightContainerCreateRequest(
            indexName,
            tenantId
        );
        assertEquals(
            mlIndexInsightContainerCreateRequest.fromActionRequest(mlIndexInsightContainerCreateRequest),
            mlIndexInsightContainerCreateRequest
        );
    }

    @Test
    public void fromActionRequest_Success_fromActionRequest() throws IOException {
        indexName = "test-abc";
        MLIndexInsightContainerCreateRequest mlIndexInsightContainerCreateRequest = new MLIndexInsightContainerCreateRequest(
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
                mlIndexInsightContainerCreateRequest.writeTo(out);
            }
        };
        MLIndexInsightContainerCreateRequest request = mlIndexInsightContainerCreateRequest.fromActionRequest(actionRequest);
        assertEquals(request.getContainerName(), indexName);
    }

}
