/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.IndexInsight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.indexInsight.IndexInsight;
import org.opensearch.ml.common.indexInsight.IndexInsightTaskStatus;
import org.opensearch.ml.common.indexInsight.MLIndexInsightType;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetResponse;

public class MLIndexInsightGetResponseTests {
    IndexInsight indexInsight;

    @Before
    public void setUp() {
        indexInsight = new IndexInsight(
            "demo-index",
            "demo-content",
            IndexInsightTaskStatus.COMPLETED,
            MLIndexInsightType.FIELD_DESCRIPTION,
            Instant.ofEpochMilli(0),
                null
        );
    }

    @Test
    public void writeToSuccess() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        MLIndexInsightGetResponse response = MLIndexInsightGetResponse.builder().indexInsight(indexInsight).build();
        response.writeTo(bytesStreamOutput);
        MLIndexInsightGetResponse parsedResponse = new MLIndexInsightGetResponse(bytesStreamOutput.bytes().streamInput());
        assertNotEquals(response, parsedResponse);
        assertNotSame(response.getIndexInsight(), parsedResponse.getIndexInsight());
        assertEquals(response.getIndexInsight().getIndex(), parsedResponse.getIndexInsight().getIndex());
        assertEquals(response.getIndexInsight().getContent(), parsedResponse.getIndexInsight().getContent());
        assertEquals(response.getIndexInsight().getStatus(), parsedResponse.getIndexInsight().getStatus());
        assertEquals(response.getIndexInsight().getTaskType(), parsedResponse.getIndexInsight().getTaskType());
        assertEquals(response.getIndexInsight().getLastUpdatedTime(), parsedResponse.getIndexInsight().getLastUpdatedTime());
    }

    @Test
    public void toXContentTest() throws IOException {
        MLIndexInsightGetResponse mlIndexInsightGetResponse = MLIndexInsightGetResponse.builder().indexInsight(indexInsight).build();
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        mlIndexInsightGetResponse.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = builder.toString();

        String expectedControllerResponse =
            "{\"index_insight\":{\"index_name\":\"demo-index\",\"content\":\"demo-content\",\"status\":\"COMPLETED\",\"task_type\":\"FIELD_DESCRIPTION\",\"last_updated_time\":0}}";
        assertEquals(expectedControllerResponse, jsonStr);
    }

    @Test
    public void fromActionResponseWithMLConnectorGetResponseSuccess() {
        MLIndexInsightGetResponse mlIndexInsightGetResponse = MLIndexInsightGetResponse.builder().indexInsight(indexInsight).build();
        MLIndexInsightGetResponse mlIndexInsightGetResponse1 = MLIndexInsightGetResponse.fromActionResponse(mlIndexInsightGetResponse);
        assertSame(mlIndexInsightGetResponse, mlIndexInsightGetResponse1);
        assertEquals(mlIndexInsightGetResponse.getIndexInsight(), mlIndexInsightGetResponse1.getIndexInsight());
    }

    @Test
    public void fromActionResponseSuccess() {
        MLIndexInsightGetResponse mlIndexInsightGetResponse = MLIndexInsightGetResponse.builder().indexInsight(indexInsight).build();
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlIndexInsightGetResponse.writeTo(out);
            }
        };
        MLIndexInsightGetResponse mlIndexInsightGetResponse1 = MLIndexInsightGetResponse.fromActionResponse(actionResponse);
        assertNotSame(mlIndexInsightGetResponse, mlIndexInsightGetResponse1);
        assertNotSame(mlIndexInsightGetResponse.getIndexInsight(), mlIndexInsightGetResponse1.getIndexInsight());
        assertEquals(mlIndexInsightGetResponse.getIndexInsight().getIndex(), mlIndexInsightGetResponse1.getIndexInsight().getIndex());
        assertEquals(mlIndexInsightGetResponse.getIndexInsight().getContent(), mlIndexInsightGetResponse1.getIndexInsight().getContent());
        assertEquals(mlIndexInsightGetResponse.getIndexInsight().getStatus(), mlIndexInsightGetResponse1.getIndexInsight().getStatus());
        assertEquals(mlIndexInsightGetResponse.getIndexInsight().getTaskType(), mlIndexInsightGetResponse1.getIndexInsight().getTaskType());
        assertEquals(
            mlIndexInsightGetResponse.getIndexInsight().getLastUpdatedTime(),
            mlIndexInsightGetResponse1.getIndexInsight().getLastUpdatedTime()
        );
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionResponseIOException() {
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException();
            }
        };
        MLIndexInsightGetResponse.fromActionResponse(actionResponse);
    }

    @Test
    public void testNullIndexInsight() {
        MLIndexInsightGetResponse response = MLIndexInsightGetResponse.builder().build();
        assertNull(response.getIndexInsight());
    }

}
