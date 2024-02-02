/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.config;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.Configuration;
import org.opensearch.ml.common.MLConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

public class MLConfigGetResponseTest {

    MLConfig mlConfig;

    @Before
    public void setUp() {
        Configuration configuration = Configuration.builder().agentId("agent_id").build();
        mlConfig = MLConfig.builder()
                .type("olly_agent")
                .configuration(configuration)
                .build();
    }

    @Test
    public void Create_mlConfigResponse_With_StreamInput() throws IOException {
        // Create a BytesStreamOutput to simulate the StreamOutput
        MLConfigGetResponse agentGetResponse = new MLConfigGetResponse(mlConfig);
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                agentGetResponse.writeTo(out);
            }
        };
        MLConfigGetResponse parsedResponse = MLConfigGetResponse.fromActionResponse(actionResponse);
        assertNotSame(agentGetResponse, parsedResponse);
        assertEquals(agentGetResponse.mlConfig, parsedResponse.mlConfig);
    }

    @Test
    public void MLConfigGetResponse_Builder() throws IOException {

        MLConfigGetResponse mlConfigGetResponse = MLConfigGetResponse.builder()
                .mlConfig(mlConfig)
                .build();

        assertEquals(mlConfigGetResponse.mlConfig, mlConfig);
    }
    @Test
    public void writeTo() throws IOException {
        //create ml agent using mlConfig and mlConfigGetResponse
        mlConfig = new MLConfig("olly_agent",new Configuration("agent_id"), Instant.EPOCH, Instant.EPOCH);
        MLConfigGetResponse mlConfigGetResponse = MLConfigGetResponse.builder()
                .mlConfig(mlConfig)
                .build();
        //use write out for both agents
        BytesStreamOutput output = new BytesStreamOutput();
        mlConfig.writeTo(output);
        mlConfigGetResponse.writeTo(output);
        MLConfig agent1 = mlConfigGetResponse.mlConfig;

        assertEquals(mlConfig.getType(), agent1.getType());
        assertEquals(mlConfig.getConfiguration(), agent1.getConfiguration());
        assertEquals(mlConfig.getCreateTime(), agent1.getCreateTime());
        assertEquals(mlConfig.getLastUpdateTime(), agent1.getLastUpdateTime());
    }

    @Test
    public void toXContent() throws IOException {
        mlConfig = new MLConfig(null, null, null, null);
        MLConfigGetResponse mlConfigGetResponse = MLConfigGetResponse.builder()
                .mlConfig(mlConfig)
                .build();
        XContentBuilder builder = XContentFactory.jsonBuilder();
        ToXContent.Params params = EMPTY_PARAMS;
        XContentBuilder getResponseXContentBuilder = mlConfigGetResponse.toXContent(builder, params);
        assertEquals(getResponseXContentBuilder, mlConfig.toXContent(builder, params));
    }

    @Test
    public void fromActionResponse_Success() throws IOException {
        MLConfigGetResponse mlConfigGetResponse = MLConfigGetResponse.builder()
                .mlConfig(mlConfig)
                .build();
        assertEquals(mlConfigGetResponse.fromActionResponse(mlConfigGetResponse), mlConfigGetResponse);

        }
    @Test
    public void fromActionResponse_Success_fromActionResponse() throws IOException {
        MLConfigGetResponse mlConfigGetResponse = MLConfigGetResponse.builder()
                .mlConfig(mlConfig)
                .build();

        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlConfigGetResponse.writeTo(out);
            }
        };
        MLConfigGetResponse response = mlConfigGetResponse.fromActionResponse(actionResponse);
        assertEquals(response.mlConfig, mlConfig);
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionResponse_IOException() {
        MLConfigGetResponse mlConfigGetResponse = MLConfigGetResponse.builder()
                .mlConfig(mlConfig)
                .build();
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException();
            }
        };
        mlConfigGetResponse.fromActionResponse(actionResponse);
    }
    }
