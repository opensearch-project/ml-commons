/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.authuser.User;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.opensearch.common.xcontent.ToXContent.EMPTY_PARAMS;

public class MLModelTests {

    MLModel mlModel;
    @Before
    public void setUp() {
        FunctionName algorithm = FunctionName.KMEANS;
        User user  = new User();
        mlModel = MLModel.builder()
                .name("some model")
                .algorithm(algorithm)
                .version(1)
                .content("some content")
                .user(user)
                .build();
    }

    @Test
    public void toXContent() throws IOException {
        MLModel mlModel = MLModel.builder().algorithm(FunctionName.KMEANS).name("model_name").version(1).content("test_content").build();
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mlModel.toXContent(builder, EMPTY_PARAMS);
        String mlModelContent = TestHelper.xContentBuilderToString(builder);
        assertEquals("{\"name\":\"model_name\",\"algorithm\":\"KMEANS\",\"version\":1,\"model_content\":\"test_content\"}", mlModelContent);
    }

    @Test
    public void toXContent_NullValue() throws IOException {
        MLModel mlModel = MLModel.builder().build();
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mlModel.toXContent(builder, EMPTY_PARAMS);
        String mlModelContent = TestHelper.xContentBuilderToString(builder);
        assertEquals("{}", mlModelContent);
    }

    @Test
    public void readInputStream_Success() throws IOException {
        readInputStream(mlModel);
    }

    public void readInputStream(MLModel mlModel) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlModel.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLModel parsedMLModel = new MLModel(streamInput);
        assertEquals(mlModel.getName(), parsedMLModel.getName());
        assertEquals(mlModel.getAlgorithm(), parsedMLModel.getAlgorithm());
        assertEquals(mlModel.getVersion(), parsedMLModel.getVersion());
        assertEquals(mlModel.getContent(), parsedMLModel.getContent());
        assertEquals(mlModel.getUser(), parsedMLModel.getUser());
    }
}
