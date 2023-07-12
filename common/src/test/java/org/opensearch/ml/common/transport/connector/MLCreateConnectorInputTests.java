/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.MLPostProcessFunction;
import org.opensearch.ml.common.connector.MLPreProcessFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class MLCreateConnectorInputTests {
    private MLCreateConnectorInput mlCreateConnectorInput;

    @Before
    public void setUp(){
        ConnectorAction.ActionType actionType = ConnectorAction.ActionType.PREDICT;
        String method = "POST";
        String url = "https://test.com";
        Map<String, String> headers = new HashMap<>();
        headers.put("api_key", "${credential.key}");
        String mlCreateConnectorRequestBody = "{\"input\": \"${parameters.input}\"}";
        String preProcessFunction = MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT;
        String postProcessFunction = MLPostProcessFunction.OPENAI_EMBEDDING;
        ConnectorAction action = new ConnectorAction(actionType, method, url, headers, mlCreateConnectorRequestBody, preProcessFunction, postProcessFunction);

        mlCreateConnectorInput = MLCreateConnectorInput.builder()
                .name("test_connector_name")
                .description("this is a test connector")
                .version("1")
                .protocol("http")
                .parameters(Map.of("input", "test input value"))
                .credential(Map.of("key", "test_key_value"))
                .actions(List.of(action))
                .access(AccessMode.PUBLIC)
                .backendRoles(Arrays.asList("role1", "role2"))
                .addAllBackendRoles(false)
                .build();
    }

    @Test
    // MLCreateConnectorInput check its parameters when created, so exception is not thrown here
    public void validate_Exception_NullMLModelName() {
        mlCreateConnectorInput.setName(null);
        MLCreateConnectorRequest mlCreateConnectorRequest = MLCreateConnectorRequest.builder()
                .mlCreateConnectorInput(mlCreateConnectorInput)
                .build();

        assertNull(mlCreateConnectorRequest.validate());
        assertNull(mlCreateConnectorRequest.getMlCreateConnectorInput().getName());
    }
}
