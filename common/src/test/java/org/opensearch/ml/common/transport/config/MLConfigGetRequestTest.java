/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.common.transport.config;

import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.Assert.assertEquals;
import static org.opensearch.action.ValidateActions.addValidationError;

public class MLConfigGetRequestTest {
    String configId;

    @Test
    public void constructor_configId() {
        configId = "test-abc";
        MLConfigGetRequest mlConfigGetRequest = new MLConfigGetRequest(configId);
        assertEquals(mlConfigGetRequest.getConfigId(),configId);
    }

    @Test
    public void writeTo() throws IOException {
        configId = "test-hij";

        MLConfigGetRequest mlConfigGetRequest = new MLConfigGetRequest(configId);
        BytesStreamOutput output = new BytesStreamOutput();
        mlConfigGetRequest.writeTo(output);

        MLConfigGetRequest mlConfigGetRequest1 = new MLConfigGetRequest(output.bytes().streamInput());

        assertEquals(mlConfigGetRequest1.getConfigId(), mlConfigGetRequest.getConfigId());
        assertEquals(mlConfigGetRequest1.getConfigId(), configId);
    }

    @Test
    public void validate_Success() {
        configId = "not-null";
        MLConfigGetRequest mlConfigGetRequest = new MLConfigGetRequest(configId);

        assertEquals(null, mlConfigGetRequest.validate());
    }

    @Test
    public void validate_Failure() {
        configId = null;
        MLConfigGetRequest mlConfigGetRequest = new MLConfigGetRequest(configId);
        assertEquals(null,mlConfigGetRequest.configId);

        ActionRequestValidationException exception = addValidationError("ML config id can't be null", null);
        mlConfigGetRequest.validate().equals(exception) ;
    }
    @Test
    public void fromActionRequest_Success()  throws IOException {
        configId = "test-lmn";
        MLConfigGetRequest mlConfigGetRequest = new MLConfigGetRequest(configId);
        assertEquals(mlConfigGetRequest.fromActionRequest(mlConfigGetRequest), mlConfigGetRequest);
    }

    @Test
    public void fromActionRequest_Success_fromActionRequest() throws IOException {
        configId = "test-opq";
        MLConfigGetRequest mlConfigGetRequest = new MLConfigGetRequest(configId);

        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlConfigGetRequest.writeTo(out);
            }
        };
        MLConfigGetRequest request = mlConfigGetRequest.fromActionRequest(actionRequest);
        assertEquals(request.configId, configId);
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionRequest_IOException() {
        configId = "test-rst";
        MLConfigGetRequest mlConfigGetRequest = new MLConfigGetRequest(configId);
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException();
            }
        };
        mlConfigGetRequest.fromActionRequest(actionRequest);
    }
}


