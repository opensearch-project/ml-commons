/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.IndexInsight;

import static org.junit.Assert.assertEquals;
import static org.opensearch.action.ValidateActions.addValidationError;
import static org.opensearch.ml.common.indexInsight.MLIndexInsightType.FIELD_DESCRIPTION;

import java.io.IOException;

import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.indexInsight.MLIndexInsightType;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetRequest;

public class MLIndexInsightGetRequestTests {
    private String indexName;
    private MLIndexInsightType mlIndexInsightType;
    private String tenantId = null;

    @Test
    public void constructor() {
        indexName = "test-abc";
        mlIndexInsightType = FIELD_DESCRIPTION;
        MLIndexInsightGetRequest mlConfigGetRequest = new MLIndexInsightGetRequest(indexName, mlIndexInsightType, tenantId, null, null);
        assertEquals(mlConfigGetRequest.getIndexName(), indexName);
        assertEquals(mlConfigGetRequest.getTargetIndexInsight(), mlIndexInsightType);
    }

    @Test
    public void writeTo() throws IOException {
        indexName = "test-abc";
        mlIndexInsightType = FIELD_DESCRIPTION;
        MLIndexInsightGetRequest mlIndexInsightGetRequest = new MLIndexInsightGetRequest(
            indexName,
            mlIndexInsightType,
            tenantId,
            null,
            null
        );
        BytesStreamOutput output = new BytesStreamOutput();
        mlIndexInsightGetRequest.writeTo(output);

        MLIndexInsightGetRequest mlIndexInsightGetRequest1 = new MLIndexInsightGetRequest(output.bytes().streamInput());

        assertEquals(mlIndexInsightGetRequest1.getIndexName(), mlIndexInsightGetRequest.getIndexName());
        assertEquals(mlIndexInsightGetRequest1.getIndexName(), indexName);
        assertEquals(mlIndexInsightGetRequest1.getTargetIndexInsight(), mlIndexInsightGetRequest.getTargetIndexInsight());
        assertEquals(mlIndexInsightGetRequest1.getTargetIndexInsight(), mlIndexInsightType);
    }

    @Test
    public void writeTo_WithTenantId() throws IOException {
        indexName = "test-abc";
        mlIndexInsightType = FIELD_DESCRIPTION;
        String tenantId = "demo_id";
        MLIndexInsightGetRequest mlIndexInsightGetRequest = new MLIndexInsightGetRequest(
            indexName,
            mlIndexInsightType,
            tenantId,
            null,
            null
        );
        BytesStreamOutput output = new BytesStreamOutput();
        mlIndexInsightGetRequest.writeTo(output);

        MLIndexInsightGetRequest mlIndexInsightGetRequest1 = new MLIndexInsightGetRequest(output.bytes().streamInput());

        assertEquals(mlIndexInsightGetRequest1.getIndexName(), mlIndexInsightGetRequest.getIndexName());
        assertEquals(mlIndexInsightGetRequest1.getIndexName(), indexName);
        assertEquals(mlIndexInsightGetRequest1.getTenantId(), mlIndexInsightGetRequest.getTenantId());
        assertEquals(mlIndexInsightGetRequest1.getTenantId(), tenantId);
        assertEquals(mlIndexInsightGetRequest1.getTargetIndexInsight(), mlIndexInsightGetRequest.getTargetIndexInsight());
        assertEquals(mlIndexInsightGetRequest1.getTargetIndexInsight(), mlIndexInsightType);
    }

    @Test
    public void validate_Success() {
        indexName = "not-null";
        mlIndexInsightType = FIELD_DESCRIPTION;
        MLIndexInsightGetRequest mlIndexInsightGetRequest = new MLIndexInsightGetRequest(
            indexName,
            mlIndexInsightType,
            tenantId,
            null,
            null
        );

        assertEquals(null, mlIndexInsightGetRequest.validate());
    }

    @Test
    public void validate_Failure_index() {
        indexName = null;
        mlIndexInsightType = FIELD_DESCRIPTION;
        MLIndexInsightGetRequest mlIndexInsightGetRequest = new MLIndexInsightGetRequest(
            indexName,
            mlIndexInsightType,
            tenantId,
            null,
            null
        );
        assertEquals(null, mlIndexInsightGetRequest.getIndexName());

        ActionRequestValidationException exception = addValidationError("Index insight's target index can't be null", null);
        mlIndexInsightGetRequest.validate().equals(exception);
    }

    @Test
    public void validate_Failure_type() {
        indexName = "not-null";
        mlIndexInsightType = null;
        MLIndexInsightGetRequest mlIndexInsightGetRequest = new MLIndexInsightGetRequest(
            indexName,
            mlIndexInsightType,
            tenantId,
            null,
            null
        );
        assertEquals(null, mlIndexInsightGetRequest.getTargetIndexInsight());

        ActionRequestValidationException exception = addValidationError("Index insight's target type can't be null", null);
        mlIndexInsightGetRequest.validate().equals(exception);
    }

    @Test
    public void fromActionRequest_Success() throws IOException {
        indexName = "test-abc";
        mlIndexInsightType = FIELD_DESCRIPTION;
        MLIndexInsightGetRequest mlIndexInsightGetRequest = new MLIndexInsightGetRequest(
            indexName,
            mlIndexInsightType,
            tenantId,
            null,
            null
        );
        assertEquals(mlIndexInsightGetRequest.fromActionRequest(mlIndexInsightGetRequest), mlIndexInsightGetRequest);
    }

    @Test
    public void fromActionRequest_Success_fromActionRequest() throws IOException {
        indexName = "test-abc";
        mlIndexInsightType = FIELD_DESCRIPTION;
        MLIndexInsightGetRequest mlIndexInsightGetRequest = new MLIndexInsightGetRequest(
            indexName,
            mlIndexInsightType,
            tenantId,
            null,
            null
        );

        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlIndexInsightGetRequest.writeTo(out);
            }
        };
        MLIndexInsightGetRequest request = mlIndexInsightGetRequest.fromActionRequest(actionRequest);
        assertEquals(request.getIndexName(), indexName);
    }
}
