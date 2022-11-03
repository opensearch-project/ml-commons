/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig.FrameworkType;
import org.opensearch.ml.common.transport.upload_chunk.MLCreateModelMetaInput;
import org.opensearch.ml.common.transport.upload_chunk.MLUploadModelChunkInput;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.rest.RestStatus;

public class RestMLCustomModelChunkActionIT extends MLCommonsRestTestCase {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setup() {

    }

    protected Response createModelMeta() throws IOException {
        Response uploadCustomModelMetaResponse = TestHelper
            .makeRequest(client(), "POST", "_plugins/_ml/models/meta", null, TestHelper.toHttpEntity(prepareModelMeta()), null);
        return uploadCustomModelMetaResponse;
    }

    public void testCreateCustomMetaModel_Success() throws IOException {
        Response customModelResponse = createModelMeta();
        assertNotNull(customModelResponse);
        HttpEntity entity = customModelResponse.getEntity();
        assertNotNull(entity);
        String entityString = TestHelper.httpEntityToString(entity);
        Map map = gson.fromJson(entityString, Map.class);
        String model_id = (String) map.get("model_id");
        Response getModelResponse = TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/models/" + model_id, null, "", null);
        assertNotNull(getModelResponse);
        assertEquals(RestStatus.OK, TestHelper.restStatus(getModelResponse));
        Map getModelMap = gson.fromJson(entityString, Map.class);
        assertEquals("CREATED", getModelMap.get("status"));
    }

    public void testCreateCustomMetaModel_PredictException() throws IOException {
        Response customModelResponse = createModelMeta();
        assertNotNull(customModelResponse);
        HttpEntity entity = customModelResponse.getEntity();
        String entityString = TestHelper.httpEntityToString(entity);
        Map map = gson.fromJson(entityString, Map.class);
        String modelId = (String) map.get("model_id");
        Response getModelResponse = TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/models/" + modelId, null, "", null);
        assertNotNull(getModelResponse);
        exceptionRule.expect(ResponseException.class);
        predictTextEmbedding(modelId);
    }

    public void testCustomModelWorkflow() throws IOException, InterruptedException {
        // upload chunk
        Response customModelResponse = createModelMeta();
        assertNotNull(customModelResponse);
        HttpEntity entity = customModelResponse.getEntity();
        String entityString = TestHelper.httpEntityToString(entity);
        Map map = gson.fromJson(entityString, Map.class);
        String modelId = (String) map.get("model_id");
        Response getModelResponse = TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/models/" + modelId, null, "", null);
        assertNotNull(getModelResponse);

        Response chunk1Response = uploadModelChunk(modelId, 1);
        entity = chunk1Response.getEntity();
        entityString = TestHelper.httpEntityToString(entity);
        assertEquals("Uploaded", gson.fromJson(entityString, Map.class).get("status"));

        Response chunk2Response = uploadModelChunk(modelId, 2);
        entity = chunk2Response.getEntity();
        entityString = TestHelper.httpEntityToString(entity);
        assertEquals("Uploaded", gson.fromJson(entityString, Map.class).get("status"));

        getModelResponse = TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/models/" + modelId, null, "", null);
        assertNotNull(getModelResponse);
        entity = getModelResponse.getEntity();
        entityString = TestHelper.httpEntityToString(entity);
        map = gson.fromJson(entityString, Map.class);
        assertEquals("UPLOADED", map.get("model_state"));

        exceptionRule.expect(Exception.class);
        exceptionRule.expectMessage("Chunk number exceeds total chunks");
        Response chunk3Response = uploadModelChunk(modelId, 3);
        assertNotNull(chunk3Response);
    }

    protected Response uploadModelChunk(final String modelId, final int chunkNumber) throws IOException {
        Response createChunkUploadResponse = TestHelper
            .makeRequest(
                client(),
                "POST",
                "_plugins/_ml/models/" + modelId + "/chunk/" + chunkNumber,
                null,
                TestHelper.toHttpEntity(prepareChunkUploadInput(modelId, chunkNumber)),
                null
            );
        assertNotNull(createChunkUploadResponse);
        HttpEntity entity = createChunkUploadResponse.getEntity();
        assertNotNull(entity);
        return createChunkUploadResponse;
    }

    private String prepareModelMeta() throws IOException {
        TextEmbeddingModelConfig config = TextEmbeddingModelConfig
            .builder()
            .allConfig("All Config")
            .embeddingDimension(1235)
            .frameworkType(FrameworkType.SENTENCE_TRANSFORMERS)
            .modelType("bert")
            .build();
        MLCreateModelMetaInput input = MLCreateModelMetaInput
            .builder()
            .name("test_model")
            .version("1")
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .modelState(MLModelState.UPLOADING)
            .modelContentHashValue("1234566775")
            .modelContentSizeInBytes(12345L)
            .totalChunks(2)
            .modelConfig(config)
            .build();
        return TestHelper.toJsonString(input);
    }

    private String prepareChunkUploadInput(final String modelId, int chunkNumber) throws IOException {
        final byte[] content = new byte[] { 1, 3, 4, 5 };
        MLUploadModelChunkInput input = MLUploadModelChunkInput
            .builder()
            .chunkNumber(chunkNumber)
            .content(content)
            .modelId(modelId)
            .build();
        return TestHelper.toJsonString(input);
    }
}
