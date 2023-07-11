/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;

import java.io.IOException;
import java.io.UncheckedIOException;

public class MLCreateConnectorRequestTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void validate_nullInput() {
        MLCreateConnectorRequest request = new MLCreateConnectorRequest((MLCreateConnectorInput)null);
        ActionRequestValidationException exception = request.validate();
        Assert.assertTrue(exception.getMessage().contains("ML Connector input can't be null"));
    }

    @Test
    public void readFromStream() throws IOException {
        MLCreateConnectorInput input = MLCreateConnectorInput.builder()
                .name("test_connector")
                .protocol("http")
                .version("1")
                .description("test")
                .addAllBackendRoles(true)
                .build();
        MLCreateConnectorRequest request = new MLCreateConnectorRequest(input);
        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        MLCreateConnectorRequest request2 = new MLCreateConnectorRequest(output.bytes().streamInput());
        Assert.assertEquals("test_connector", request2.getMlCreateConnectorInput().getName());
        Assert.assertEquals("http", request2.getMlCreateConnectorInput().getProtocol());
        Assert.assertEquals("1", request2.getMlCreateConnectorInput().getVersion());
        Assert.assertEquals("test", request2.getMlCreateConnectorInput().getDescription());
    }

    @Test
    public void fromActionRequest() {
        MLCreateConnectorInput input = MLCreateConnectorInput.builder()
                .name("test_connector")
                .protocol("http")
                .version("1")
                .description("test")
                .build();
        ActionRequest request = new MLCreateConnectorRequest(input);
        MLCreateConnectorRequest request2 = MLCreateConnectorRequest.fromActionRequest(request);
        Assert.assertEquals("test_connector", request2.getMlCreateConnectorInput().getName());
        Assert.assertEquals("http", request2.getMlCreateConnectorInput().getProtocol());
        Assert.assertEquals("1", request2.getMlCreateConnectorInput().getVersion());
        Assert.assertEquals("test", request2.getMlCreateConnectorInput().getDescription());
    }

    @Test
    public void fromActionRequest_Exception() {
        exceptionRule.expect(UncheckedIOException.class);
        exceptionRule.expectMessage("Failed to parse ActionRequest into MLCreateConnectorRequest");
        ActionRequest request = new MLConnectorGetRequest("test_id", true);
        MLCreateConnectorRequest.fromActionRequest(request);
    }
}
