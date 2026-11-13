/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.rest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.TextSimilarityInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.utils.TestHelper;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

/**
 * Runs the Voyage AI connector blueprints against the live API. The tests are skipped when
 * VOYAGE_API_KEY is not set. They use the default trusted endpoint regex on purpose, so they also
 * prove that api.voyageai.com is trusted out of the box.
 */
@Log4j2
public class RestVoyageInferenceIT extends MLCommonsRestTestCase {
    private final String VOYAGE_KEY = System.getenv("VOYAGE_API_KEY");

    private static final String EMBEDDING_BODY =
        "{ \\\"input\\\": ${parameters.input}, \\\"model\\\": \\\"${parameters.model}\\\", \\\"input_type\\\": \\\"${parameters.input_type}\\\" }";
    private static final String RERANK_BODY =
        "{ \\\"query\\\": \\\"${parameters.query}\\\", \\\"documents\\\": ${parameters.documents}, \\\"model\\\": \\\"${parameters.model}\\\" }";

    private String connectorBody(String name, String parameters, String endpoint, String requestBody, String pre, String post)
        throws Exception {
        String template = Files
            .readString(
                Path
                    .of(
                        RestMLPredictionAction.class
                            .getClassLoader()
                            .getResource("org/opensearch/ml/rest/templates/VoyageConnectorBodies.json")
                            .toURI()
                    )
            );
        return String.format(template, name, parameters, VOYAGE_KEY, endpoint, requestBody, pre, post);
    }

    private Map embed(String modelId, List<String> docs) throws IOException {
        MLInput mlInput = MLInput
            .builder()
            .inputDataset(TextDocsInputDataSet.builder().docs(docs).build())
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .build();
        return predictTextEmbeddingModel(modelId, mlInput);
    }

    /** One inference result is returned per request that was sent, so flatten the tensors in order. */
    private List<List<Double>> vectorsOf(Map inferenceResult) {
        List<List<Double>> vectors = new ArrayList<>();
        for (Object result : (List) inferenceResult.get("inference_results")) {
            for (Object tensor : (List) ((Map) result).get("output")) {
                vectors.add((List<Double>) ((Map) tensor).get("data"));
            }
        }
        return vectors;
    }

    @SneakyThrows
    public void test_voyageEmbedding_batchesByStepSizeAndKeepsInputOrder() {
        if (StringUtils.isEmpty(VOYAGE_KEY)) {
            log.info("VOYAGE_API_KEY is null, skipping the test!");
            return;
        }
        String parameters = "\"model\": \"voyage-4-lite\", \"input_type\": \"document\", \"input_docs_processed_step_size\": \"2\"";
        String body = connectorBody(
            "embedding",
            parameters,
            "embeddings",
            EMBEDDING_BODY,
            "connector.pre_process.voyage.embedding",
            "connector.post_process.voyage.embedding"
        );
        String modelId = registerRemoteModel(body, "voyage-embedding-test", true);

        List<String> docs = List.of("alpha", "completely different second text about the sea", "gamma", "delta", "epsilon");
        Map batched = embed(modelId, docs);
        // 5 documents with a step size of 2 are sent as 3 requests
        assertEquals(3, ((List) batched.get("inference_results")).size());
        List<List<Double>> batchedVectors = vectorsOf(batched);
        assertEquals(docs.size(), batchedVectors.size());
        assertEquals(1024, batchedVectors.get(0).size());

        // Every vector must belong to the document at the same position, whichever request it came from.
        for (int i = 0; i < docs.size(); i++) {
            Map single = embed(modelId, List.of(docs.get(i)));
            List<Double> expected = vectorsOf(single).get(0);
            List<Double> actual = batchedVectors.get(i);
            double dot = 0;
            for (int d = 0; d < expected.size(); d++) {
                dot += expected.get(d) * actual.get(d);
            }
            assertTrue("vector " + i + " does not match its input, cosine=" + dot, dot > 0.99);
        }
    }

    @SneakyThrows
    public void test_voyageRerank_returnsScoresInInputOrder() {
        if (StringUtils.isEmpty(VOYAGE_KEY)) {
            log.info("VOYAGE_API_KEY is null, skipping the test!");
            return;
        }
        String body = connectorBody(
            "rerank",
            "\"model\": \"rerank-3\"",
            "rerank",
            RERANK_BODY,
            "connector.pre_process.voyage.rerank",
            "connector.post_process.voyage.rerank"
        );
        String modelId = registerRemoteModel(body, "voyage-rerank-test", true);
        MLInput mlInput = MLInput
            .builder()
            .algorithm(FunctionName.TEXT_SIMILARITY)
            .inputDataset(
                TextSimilarityInputDataSet
                    .builder()
                    .queryText("What is the capital of France?")
                    .textDocs(List.of("Cats sleep a lot.", "Berlin is in Germany.", "Paris is the capital of France."))
                    .build()
            )
            .build();
        Response response = TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/_predict/TEXT_SIMILARITY/" + modelId,
                null,
                TestHelper.toJsonString(mlInput),
                null
            );
        Map result = parseResponseToMap(response);
        List results = (List) result.get("inference_results");
        assertEquals(1, results.size());
        List tensors = (List) ((Map) results.get(0)).get("output");
        assertEquals(3, tensors.size());
        double[] scores = new double[3];
        for (int i = 0; i < 3; i++) {
            Map tensor = (Map) tensors.get(i);
            assertEquals("similarity", tensor.get("name"));
            scores[i] = ((Number) ((List) tensor.get("data")).get(0)).doubleValue();
        }
        assertTrue(scores[2] > scores[0] && scores[2] > scores[1]);
    }

    @SneakyThrows
    public void test_voyageEmbedding_withoutModel_failsWithClearError() {
        if (StringUtils.isEmpty(VOYAGE_KEY)) {
            log.info("VOYAGE_API_KEY is null, skipping the test!");
            return;
        }
        String body = connectorBody(
            "embedding-no-model",
            "\"input_type\": \"document\"",
            "embeddings",
            EMBEDDING_BODY,
            "connector.pre_process.voyage.embedding",
            "connector.post_process.voyage.embedding"
        );
        String modelId = registerRemoteModel(body, "voyage-no-model-test", true);
        MLInput mlInput = MLInput
            .builder()
            .inputDataset(TextDocsInputDataSet.builder().docs(List.of("hello")).build())
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .build();
        try {
            Response response = TestHelper
                .makeRequest(
                    client(),
                    "POST",
                    "/_plugins/_ml/_predict/TEXT_EMBEDDING/" + modelId,
                    null,
                    TestHelper.toJsonString(mlInput),
                    null
                );
            fail("predict without a model must fail, got status " + response.getStatusLine());
        } catch (ResponseException e) {
            String message = TestHelper.httpEntityToString(e.getResponse().getEntity());
            assertTrue(message, message.contains("Some parameter placeholder not filled in payload: model"));
        }
    }
}
