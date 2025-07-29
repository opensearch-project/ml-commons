/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.upload_chunk;

import static org.junit.Assert.assertEquals;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.ml.common.model.BaseModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.model.RemoteModelConfig;

public class MLRegisterModelMetaInputTest {

    Function<XContentParser, MLRegisterModelMetaInput> function = parser -> {
        try {
            return MLRegisterModelMetaInput.parse(parser);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse MLRegisterModelMetaInput", e);
        }
    };
    BaseModelConfig config;
    MLRegisterModelMetaInput mLRegisterModelMetaInput;

    @Before
    public void setup() {
        Map<String, Object> additionalConfig = new HashMap<>();
        additionalConfig.put("test_key", "test_value");

        config = new BaseModelConfig(
            "Model Type",
            "\"test_key1\":\"test_value1\"",
            additionalConfig,
            768,
            BaseModelConfig.FrameworkType.SENTENCE_TRANSFORMERS,
            BaseModelConfig.PoolingMode.MEAN,
            false,
            null,
            null,
            null
        );

        mLRegisterModelMetaInput = new MLRegisterModelMetaInput(
            "Model Name",
            FunctionName.BATCH_RCF,
            "model_group_id",
            "1.0",
            "Model Description",
            null,
            null,
            MLModelFormat.TORCH_SCRIPT,
            MLModelState.DEPLOYING,
            200L,
            "123",
            config,
            null,
            2,
            null,
            null,
            false,
            false,
            false,
            null
        );
    }

    @Test
    public void parse_MLRegisterModelMetaInput() throws IOException {
        TestHelper.testParse(mLRegisterModelMetaInput, function);
    }

    @Test
    public void readInputStream_Success() throws IOException {
        readInputStream(mLRegisterModelMetaInput);
    }

    private void readInputStream(MLRegisterModelMetaInput input) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        input.writeTo(bytesStreamOutput);
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLRegisterModelMetaInput newInput = new MLRegisterModelMetaInput(streamInput);
        assertEquals(input.getName(), newInput.getName());
        assertEquals(input.getFunctionName(), newInput.getFunctionName());
        assertEquals(input.getModelGroupId(), newInput.getModelGroupId());
        assertEquals(input.getVersion(), newInput.getVersion());
        assertEquals(input.getDescription(), newInput.getDescription());
        assertEquals(input.getModelFormat(), newInput.getModelFormat());
        assertEquals(input.getModelConfig().getAllConfig(), newInput.getModelConfig().getAllConfig());
        assertEquals(input.getModelConfig().getModelType(), newInput.getModelConfig().getModelType());
        assertEquals(input.getModelFormat(), newInput.getModelFormat());
        assertEquals(input.getModelState(), newInput.getModelState());
        assertEquals(input.getModelContentSizeInBytes(), newInput.getModelContentSizeInBytes());
        assertEquals(input.getModelContentHashValue(), newInput.getModelContentHashValue());
        assertEquals(input.getTotalChunks(), newInput.getTotalChunks());
        assertEquals(input.getBackendRoles(), newInput.getBackendRoles());
        assertEquals(input.getIsAddAllBackendRoles(), newInput.getIsAddAllBackendRoles());
        assertEquals(input.getAccessMode(), newInput.getAccessMode());
        assertEquals(input.getDoesVersionCreateModelGroup(), newInput.getDoesVersionCreateModelGroup());
        assertEquals(input.getIsHidden(), newInput.getIsHidden());

        if (input.getModelConfig() instanceof RemoteModelConfig) {
            RemoteModelConfig originalConfig = (RemoteModelConfig) input.getModelConfig();
            RemoteModelConfig newConfig = (RemoteModelConfig) newInput.getModelConfig();

            assertEquals(originalConfig.getModelType(), newConfig.getModelType());
            assertEquals(originalConfig.getAllConfig(), newConfig.getAllConfig());
            assertEquals(originalConfig.getAdditionalConfig(), newConfig.getAdditionalConfig());
            assertEquals(originalConfig.getEmbeddingDimension(), newConfig.getEmbeddingDimension());
            assertEquals(originalConfig.getFrameworkType(), newConfig.getFrameworkType());
            assertEquals(originalConfig.getPoolingMode(), newConfig.getPoolingMode());
            assertEquals(originalConfig.getModelMaxLength(), newConfig.getModelMaxLength());
        }
    }

    @Test
    public void testToXContent() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mLRegisterModelMetaInput.toXContent(builder, EMPTY_PARAMS);
        String mlModelContent = TestHelper.xContentBuilderToString(builder);
        final String expected = "{\"name\":\"Model Name\",\"function_name\":\"BATCH_RCF\",\"model_group_id\":"
            + "\"model_group_id\",\"version\":\"1.0\",\"description\":\"Model Description\","
            + "\"model_format\":\"TORCH_SCRIPT\",\"model_state\":\"DEPLOYING\","
            + "\"model_content_size_in_bytes\":200,\"model_content_hash_value\":\"123\","
            + "\"model_config\":{\"model_type\":\"Model Type\",\"all_config\":\"\\\"test_key1\\\":\\\"test_value1\\\"\","
            + "\"additional_config\":{\"test_key\":\"test_value\"},\"embedding_dimension\":768,"
            + "\"framework_type\":\"SENTENCE_TRANSFORMERS\",\"pooling_mode\":\"MEAN\"},\"total_chunks\":2,"
            + "\"add_all_backend_roles\":false,\"does_version_create_model_group\":false,\"is_hidden\":false}";
        assertEquals(expected, mlModelContent);
    }

    @Test
    public void readInputStream_RemoteModelConfig() throws IOException {
        Map<String, Object> additionalConfig = new HashMap<>();
        additionalConfig.put("space_type", "l2");

        RemoteModelConfig remoteConfig = RemoteModelConfig
            .builder()
            .modelType("text_embedding")
            .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
            .additionalConfig(additionalConfig)
            .embeddingDimension(768)
            .frameworkType(RemoteModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
            .poolingMode(RemoteModelConfig.PoolingMode.MEAN)
            .modelMaxLength(512)
            .build();

        MLRegisterModelMetaInput remoteInput = new MLRegisterModelMetaInput(
            "Remote Model",
            FunctionName.REMOTE,
            "remote_model_group",
            "1.0",
            "Remote Model Description",
            null,
            null,
            MLModelFormat.TORCH_SCRIPT,
            MLModelState.DEPLOYING,
            200L,
            "123",
            remoteConfig,
            null,
            2,
            null,
            null,
            false,
            false,
            false,
            null
        );
        readInputStream(remoteInput);
    }
}
