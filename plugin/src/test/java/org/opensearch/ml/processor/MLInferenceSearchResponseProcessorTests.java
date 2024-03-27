/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.processor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.opensearch.ml.processor.MLInferenceSearchResponseProcessor.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.client.Client;
import org.opensearch.common.document.DocumentField;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableMap;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.SearchModule;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.pipeline.PipelineProcessingContext;
import org.opensearch.test.AbstractBuilderTestCase;

public class MLInferenceSearchResponseProcessorTests extends AbstractBuilderTestCase {
    @Mock
    private Client client;

    @Mock
    private PipelineProcessingContext responseContext;

    static public final NamedXContentRegistry TEST_XCONTENT_REGISTRY_FOR_QUERY = new NamedXContentRegistry(
        new SearchModule(Settings.EMPTY, List.of()).getNamedXContents()
    );
    private static final String PROCESSOR_TAG = "inference";
    private static final String DESCRIPTION = "inference_test";

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    /**
     * Tests that an exception is thrown when the `processResponse` method is called, as this processor
     * makes asynchronous calls.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testProcessResponseException() throws Exception {
        String modelInputField = "inputs";
        String originalDocumentField = "query.term.text.value";
        String newDocumentField = "query.term.text.value";
        String modelOutputField = "response";
        MLInferenceSearchResponseProcessor responseProcessor = getMlInferenceSearchResponseProcessorSinglePairMapping(
            modelOutputField,
            modelInputField,
            originalDocumentField,
            newDocumentField,
            false,
            false,
            false
        );
        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);

        try {
            responseProcessor.processResponse(request, response);

        } catch (Exception e) {
            assertEquals("ML inference search response processor make asynchronous calls and does not call processRequest", e.getMessage());
        }
    }

    public void testProcessResponseSuccess() throws Exception {
        String modelInputField = "inputs";
        String originalDocumentField = "text";
        String newDocumentField = "text_embedding";
        String modelOutputField = "response";
        MLInferenceSearchResponseProcessor responseProcessor = getMlInferenceSearchResponseProcessorSinglePairMapping(
            modelOutputField,
            modelInputField,
            originalDocumentField,
            newDocumentField,
            false,
            false,
            false
        );
        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);

        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", Arrays.asList(0.0, 1.0, 2.0, 3.0, 4.0)))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits().length, 5);
                System.out.println(newSearchResponse.getHits().getHits()[0].getSourceAsString());
                System.out.println(newSearchResponse.getHits().getHits()[0].getSourceAsMap().get("text_embedding"));
                assertEquals(newSearchResponse.getHits().getHits()[0].getSourceAsMap().get("text_embedding"), 0.0);
                assertEquals(newSearchResponse.getHits().getHits()[1].getSourceAsMap().get("text_embedding"), 1.0);
                assertEquals(newSearchResponse.getHits().getHits()[2].getSourceAsMap().get("text_embedding"), 2.0);
                assertEquals(newSearchResponse.getHits().getHits()[3].getSourceAsMap().get("text_embedding"), 3.0);
                assertEquals(newSearchResponse.getHits().getHits()[4].getSourceAsMap().get("text_embedding"), 4.0);
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }
        };

        responseProcessor.processResponseAsync(request, response, responseContext, listener);
    }

    public void testProcessResponseListOfEmbeddingsSuccess() throws Exception {
        /**
         * sample response before inference
         * {
         * { "text" : "value 0" },
         * { "text" : "value 1" },
         * { "text" : "value 2" },
         * { "text" : "value 3" },
         * { "text" : "value 4" }
         * }
         *
         * sample response after inference
         *  { "text" : "value 0", "text_embedding":[0.1, 0.2]},
         *  { "text" : "value 1", "text_embedding":[0.2, 0.2]},
         *  { "text" : "value 2", "text_embedding":[0.3, 0.2]},
         *  { "text" : "value 3"，"text_embedding":[0.4, 0.2]},
         *  { "text" : "value 4"，"text_embedding":[0.5, 0.2]}
         */

        String modelInputField = "inputs";
        String originalDocumentField = "text";
        String newDocumentField = "text_embedding";
        String modelOutputField = "response";
        MLInferenceSearchResponseProcessor responseProcessor = getMlInferenceSearchResponseProcessorSinglePairMapping(
            modelOutputField,
            modelInputField,
            originalDocumentField,
            newDocumentField,
            false,
            false,
            false
        );
        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);

        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(
                ImmutableMap
                    .of(
                        "response",
                        Arrays
                            .asList(
                                Arrays.asList(0.1, 0.2),
                                Arrays.asList(0.2, 0.2),
                                Arrays.asList(0.3, 0.2),
                                Arrays.asList(0.4, 0.2),
                                Arrays.asList(0.5, 0.2)
                            )
                    )
            )
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits().length, 5);
                System.out.println("printing document.. ");
                System.out.println(newSearchResponse.getHits().getHits()[0].getSourceAsString());
                System.out.println(newSearchResponse.getHits().getHits()[0].getSourceAsMap().get("text_embedding"));
                assertEquals(newSearchResponse.getHits().getHits()[0].getSourceAsMap().get("text_embedding"), Arrays.asList(0.1, 0.2));
                assertEquals(newSearchResponse.getHits().getHits()[1].getSourceAsMap().get("text_embedding"), Arrays.asList(0.2, 0.2));
                assertEquals(newSearchResponse.getHits().getHits()[2].getSourceAsMap().get("text_embedding"), Arrays.asList(0.3, 0.2));
                assertEquals(newSearchResponse.getHits().getHits()[3].getSourceAsMap().get("text_embedding"), Arrays.asList(0.4, 0.2));
                assertEquals(newSearchResponse.getHits().getHits()[4].getSourceAsMap().get("text_embedding"), Arrays.asList(0.5, 0.2));
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }
        };

        responseProcessor.processResponseAsync(request, response, responseContext, listener);
    }

    public void testProcessResponseListOfEmbeddingsMissingOneInputIgnoreMissingSuccess() throws Exception {
        /**
         * sample response before inference
         * {
         * { "text" : "value 0" },
         * { "text" : "value 1" },
         * { "textMissing" : "value 2" },
         * { "text" : "value 3" },
         * { "text" : "value 4" }
         * }
         *
         * sample response after inference
         *  { "text" : "value 0", "text_embedding":[0.1, 0.2]},
         *  { "text" : "value 1", "text_embedding":[0.2, 0.2]},
         *  { "textMissing" : "value 2"},
         *  { "text" : "value 3"，"text_embedding":[0.4, 0.2]},
         *  { "text" : "value 4"，"text_embedding":[0.5, 0.2]}
         */

        String modelInputField = "inputs";
        String originalDocumentField = "text";
        String newDocumentField = "text_embedding";
        String modelOutputField = "response";
        MLInferenceSearchResponseProcessor responseProcessor = getMlInferenceSearchResponseProcessorSinglePairMapping(
            modelOutputField,
            modelInputField,
            originalDocumentField,
            newDocumentField,
            false,
            false,
            true
        );
        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponseMissingField(5, true, fieldName);

        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(
                ImmutableMap
                    .of(
                        "response",
                        Arrays.asList(Arrays.asList(0.1, 0.2), Arrays.asList(0.2, 0.2), Arrays.asList(0.4, 0.2), Arrays.asList(0.5, 0.2))
                    )
            )
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits().length, 5);

                System.out.println("printing document.. ");
                System.out.println(newSearchResponse.getHits().getHits()[0].getSourceAsString());
                System.out.println(newSearchResponse.getHits().getHits()[0].getSourceAsMap().get("text_embedding"));
                assertEquals(newSearchResponse.getHits().getHits()[0].getSourceAsMap().get("text_embedding"), Arrays.asList(0.1, 0.2));
                assertEquals(newSearchResponse.getHits().getHits()[1].getSourceAsMap().get("text_embedding"), Arrays.asList(0.2, 0.2));

                assertEquals(newSearchResponse.getHits().getHits()[3].getSourceAsMap().get("text_embedding"), Arrays.asList(0.4, 0.2));
                assertEquals(newSearchResponse.getHits().getHits()[4].getSourceAsMap().get("text_embedding"), Arrays.asList(0.5, 0.2));

            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }
        };

        responseProcessor.processResponseAsync(request, response, responseContext, listener);
    }

    public void testProcessResponseTwoRoundsOfPredictionSuccess() {
        String modelInputField = "inputs";
        String modelOutputField = "response";

        // document fields for first round of prediction
        String originalDocumentField = "text";
        String newDocumentField = "text_embedding";

        // document fields for second round of prediction
        String originalDocumentField1 = "image";
        String newDocumentField1 = "image_embedding";

        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put(modelInputField, originalDocumentField);
        inputMap.add(input);

        Map<String, String> input1 = new HashMap<>();
        input1.put(modelInputField, originalDocumentField1);
        inputMap.add(input1);

        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);

        Map<String, String> output2 = new HashMap<>();
        output2.put(newDocumentField1, modelOutputField);
        outputMap.add(output2);

        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            inputMap,
            outputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY
        );
        SearchRequest request = getSearchRequest();
        SearchResponse response = getSearchResponseTwoFields(5, true, originalDocumentField, originalDocumentField1);

        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", Arrays.asList(0.0, 1.0, 2.0, 3.0, 4.0)))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits().length, 5);
                System.out.println(newSearchResponse.getHits().getHits()[0].getSourceAsString());
                System.out.println(newSearchResponse.getHits().getHits()[0].getSourceAsMap().get(newDocumentField));
                assertEquals(newSearchResponse.getHits().getHits()[0].getSourceAsMap().get(newDocumentField), 0.0);
                assertEquals(newSearchResponse.getHits().getHits()[1].getSourceAsMap().get(newDocumentField), 1.0);
                assertEquals(newSearchResponse.getHits().getHits()[2].getSourceAsMap().get(newDocumentField), 2.0);
                assertEquals(newSearchResponse.getHits().getHits()[3].getSourceAsMap().get(newDocumentField), 3.0);
                assertEquals(newSearchResponse.getHits().getHits()[4].getSourceAsMap().get(newDocumentField), 4.0);

                assertEquals(newSearchResponse.getHits().getHits()[0].getSourceAsMap().get(newDocumentField1), 0.0);
                assertEquals(newSearchResponse.getHits().getHits()[1].getSourceAsMap().get(newDocumentField1), 1.0);
                assertEquals(newSearchResponse.getHits().getHits()[2].getSourceAsMap().get(newDocumentField1), 2.0);
                assertEquals(newSearchResponse.getHits().getHits()[3].getSourceAsMap().get(newDocumentField1), 3.0);
                assertEquals(newSearchResponse.getHits().getHits()[4].getSourceAsMap().get(newDocumentField1), 4.0);
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }
        };

        responseProcessor.processResponseAsync(request, response, responseContext, listener);
    }

    private static SearchRequest getSearchRequest() {
        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery);
        SearchRequest request = new SearchRequest().source(source);
        return request;
    }

    /**
     * Helper method to create an instance of the MLInferenceSearchResponseProcessor with the specified parameters in
     * single pair of input and output mapping.
     *
     * @param modelInputField       the model input field name
     * @param originalDocumentField the original query field name
     * @param newDocumentField      the new document field name
     * @param override              the flag indicating whether to override existing document field
     * @param ignoreFailure         the flag indicating whether to ignore failures or not
     * @param ignoreMissing         the flag indicating whether to ignore missing fields or not
     * @return an instance of the MLInferenceSearchResponseProcessor
     */
    private MLInferenceSearchResponseProcessor getMlInferenceSearchResponseProcessorSinglePairMapping(
        String modelOutputField,
        String modelInputField,
        String originalDocumentField,
        String newDocumentField,
        boolean override,
        boolean ignoreFailure,
        boolean ignoreMissing
    ) {
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put(modelInputField, originalDocumentField);
        inputMap.add(input);

        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);

        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            inputMap,
            outputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            ignoreMissing,
            "remote",
            false,
            ignoreFailure,
            override,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY
        );
        return responseProcessor;
    }

    private SearchResponse getSearchResponse(int size, boolean includeMapping, String fieldName) {
        SearchHit[] hits = new SearchHit[size];
        System.out.println("printing hit.. ");
        for (int i = 0; i < size; i++) {
            Map<String, DocumentField> searchHitFields = new HashMap<>();
            if (includeMapping) {
                searchHitFields.put(fieldName, new DocumentField("value " + i, Collections.emptyList()));
            }
            searchHitFields.put(fieldName, new DocumentField("value " + i, Collections.emptyList()));
            hits[i] = new SearchHit(i, "doc " + i, searchHitFields, Collections.emptyMap());
            hits[i].sourceRef(new BytesArray("{ \"" + fieldName + "\" : \"value " + i + "\" }"));
            hits[i].score(i);

            System.out.println(hits[i].getSourceAsString());
        }
        SearchHits searchHits = new SearchHits(hits, new TotalHits(size * 2L, TotalHits.Relation.EQUAL_TO), size);
        SearchResponseSections searchResponseSections = new SearchResponseSections(searchHits, null, null, false, false, null, 0);
        return new SearchResponse(searchResponseSections, null, 1, 1, 0, 10, null, null);
    }

    private SearchResponse getSearchResponseMissingField(int size, boolean includeMapping, String fieldName) {
        SearchHit[] hits = new SearchHit[size];
        System.out.println("printing hit.. ");
        for (int i = 0; i < size; i++) {

            if (i == (size % 3)) {
                Map<String, DocumentField> searchHitFields = new HashMap<>();
                if (includeMapping) {
                    searchHitFields.put(fieldName + "Missing", new DocumentField("value " + i, Collections.emptyList()));
                }
                searchHitFields.put(fieldName + "Missing", new DocumentField("value " + i, Collections.emptyList()));
                hits[i] = new SearchHit(i, "doc " + i, searchHitFields, Collections.emptyMap());
                hits[i].sourceRef(new BytesArray("{ \"" + fieldName + "Missing" + "\" : \"value " + i + "\" }"));
                hits[i].score(i);

                System.out.println(hits[i].getSourceAsString());
            } else {
                Map<String, DocumentField> searchHitFields = new HashMap<>();
                if (includeMapping) {
                    searchHitFields.put(fieldName, new DocumentField("value " + i, Collections.emptyList()));
                }
                searchHitFields.put(fieldName, new DocumentField("value " + i, Collections.emptyList()));
                hits[i] = new SearchHit(i, "doc " + i, searchHitFields, Collections.emptyMap());
                hits[i].sourceRef(new BytesArray("{ \"" + fieldName + "\" : \"value " + i + "\" }"));
                hits[i].score(i);

                System.out.println(hits[i].getSourceAsString());
            }
        }
        SearchHits searchHits = new SearchHits(hits, new TotalHits(size * 2L, TotalHits.Relation.EQUAL_TO), size);
        SearchResponseSections searchResponseSections = new SearchResponseSections(searchHits, null, null, false, false, null, 0);
        return new SearchResponse(searchResponseSections, null, 1, 1, 0, 10, null, null);
    }

    private SearchResponse getSearchResponseTwoFields(int size, boolean includeMapping, String fieldName, String fieldName1) {
        SearchHit[] hits = new SearchHit[size];
        System.out.println("printing hit.. ");
        for (int i = 0; i < size; i++) {
            Map<String, DocumentField> searchHitFields = new HashMap<>();
            if (includeMapping) {
                searchHitFields.put(fieldName, new DocumentField("value " + i, Collections.emptyList()));
                searchHitFields.put(fieldName1, new DocumentField("value " + i, Collections.emptyList()));
            }
            searchHitFields.put(fieldName, new DocumentField("value " + i, Collections.emptyList()));
            searchHitFields.put(fieldName1, new DocumentField("value " + i, Collections.emptyList()));
            hits[i] = new SearchHit(i, "doc " + i, searchHitFields, Collections.emptyMap());
            hits[i]
                .sourceRef(
                    new BytesArray(
                        "{ \"" + fieldName + "\" : \"value " + i + "\", " + "\"" + fieldName1 + "\" : \"value " + i + "\" " + " }"
                    )
                );
            hits[i].score(i);

            System.out.println(hits[i].getSourceAsString());
        }
        SearchHits searchHits = new SearchHits(hits, new TotalHits(size * 2L, TotalHits.Relation.EQUAL_TO), size);
        SearchResponseSections searchResponseSections = new SearchResponseSections(searchHits, null, null, false, false, null, 0);
        return new SearchResponse(searchResponseSections, null, 1, 1, 0, 10, null, null);
    }
}
