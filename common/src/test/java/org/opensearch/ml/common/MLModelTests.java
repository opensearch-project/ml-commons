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
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.authuser.User;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.model.MLModelTaskType;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;

import java.io.IOException;
import java.time.Instant;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.opensearch.common.xcontent.ToXContent.EMPTY_PARAMS;

public class MLModelTests {

    MLModel mlModel;
    TextEmbeddingModelConfig config;
    Function<XContentParser, MLModel> function;
    @Before
    public void setUp() {
        FunctionName algorithm = FunctionName.KMEANS;
        User user  = new User();
        config = TextEmbeddingModelConfig.builder()
                .modelType("testModelType")
                .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
                .frameworkType(TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
                .embeddingDimension(100)
                .build();
        Instant now = Instant.now();
        mlModel = MLModel.builder()
                .name("some model")
                .algorithm(algorithm)
                .version(1)
                .content("some content")
                .user(user)
                .description("test description")
                .modelFormat(MLModelFormat.ONNX)
                .modelTaskType(MLModelTaskType.TEXT_EMBEDDING)
                .modelState(MLModelState.LOADED)
                .modelContentSizeInBytes(10_000_000l)
                .modelContentHash("test_hash")
                .modelConfig(config)
                .createdTime(now)
                .latestUploadedTime(now)
                .latestLoadedTime(now)
                .latestUnloadedTime(now)
                .modelId("model_id")
                .chunkNumber(1)
                .totalChunks(10)
                .build();
        function = parser -> {
            try {
                return MLModel.parse(parser);
            } catch (IOException e) {
                throw new RuntimeException("Failed to parse TextEmbeddingModelConfig", e);
            }
        };
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
    public void parse() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mlModel.toXContent(builder, EMPTY_PARAMS);
        String mlModelContent = TestHelper.xContentBuilderToString(builder);
        TestHelper.testParseFromString(config, mlModelContent, function);
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
