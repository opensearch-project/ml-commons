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
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightContainerPutRequest;

public class MLIndexInsightContainerPutRequestTests {
    private String indexName;
    private String tenantId = null;

    @Test
    public void constructor() {
        indexName = "test-abc";
        MLIndexInsightContainerPutRequest mlIndexInsightContainerPutRequest = new MLIndexInsightContainerPutRequest(indexName, tenantId);
        assertEquals(mlIndexInsightContainerPutRequest.getIndexName(), indexName);
    }

    @Test
    public void writeTo() throws IOException {
        indexName = "test-abc";
        MLIndexInsightContainerPutRequest mlIndexInsightContainerPutRequest = new MLIndexInsightContainerPutRequest(indexName, tenantId);
        BytesStreamOutput output = new BytesStreamOutput();
        mlIndexInsightContainerPutRequest.writeTo(output);

        MLIndexInsightContainerPutRequest mlIndexInsightContainerPutRequest1 = new MLIndexInsightContainerPutRequest(
            output.bytes().streamInput()
        );

        assertEquals(mlIndexInsightContainerPutRequest1.getIndexName(), mlIndexInsightContainerPutRequest.getIndexName());
        assertEquals(mlIndexInsightContainerPutRequest1.getIndexName(), indexName);
    }

    @Test
    public void validate_Success() {
        indexName = "not-null";
        MLIndexInsightContainerPutRequest mlIndexInsightContainerPutRequest = new MLIndexInsightContainerPutRequest(indexName, tenantId);

        assertEquals(null, mlIndexInsightContainerPutRequest.validate());
    }

    @Test
    public void validate_Failure_index() {
        indexName = null;
        MLIndexInsightContainerPutRequest mlIndexInsightContainerPutRequest = new MLIndexInsightContainerPutRequest(indexName, tenantId);
        assertEquals(null, mlIndexInsightContainerPutRequest.getIndexName());

        ActionRequestValidationException exception = addValidationError("Index Insight's container index can't be null", null);
        mlIndexInsightContainerPutRequest.validate().equals(exception);
    }

    @Test
    public void fromActionRequest_Success() throws IOException {
        indexName = "test-abc";
        MLIndexInsightContainerPutRequest mlIndexInsightContainerPutRequest = new MLIndexInsightContainerPutRequest(indexName, tenantId);
        assertEquals(
            mlIndexInsightContainerPutRequest.fromActionRequest(mlIndexInsightContainerPutRequest),
            mlIndexInsightContainerPutRequest
        );
    }

    @Test
    public void fromActionRequest_Success_fromActionRequest() throws IOException {
        indexName = "test-abc";
        MLIndexInsightContainerPutRequest mlIndexInsightContainerPutRequest = new MLIndexInsightContainerPutRequest(indexName, tenantId);

        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlIndexInsightContainerPutRequest.writeTo(out);
            }
        };
        MLIndexInsightContainerPutRequest request = mlIndexInsightContainerPutRequest.fromActionRequest(actionRequest);
        assertEquals(request.getIndexName(), indexName);
    }

}
