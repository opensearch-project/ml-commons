/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.processor;

import static java.lang.Math.max;
import static org.opensearch.ml.processor.InferenceProcessorAttributes.INPUT_MAP;
import static org.opensearch.ml.processor.InferenceProcessorAttributes.MAX_PREDICTION_TASKS;
import static org.opensearch.ml.processor.InferenceProcessorAttributes.MODEL_CONFIG;
import static org.opensearch.ml.processor.InferenceProcessorAttributes.MODEL_ID;
import static org.opensearch.ml.processor.InferenceProcessorAttributes.OUTPUT_MAP;
import static org.opensearch.ml.processor.MLInferenceIngestProcessor.OVERRIDE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.GroupedActionListener;
import org.opensearch.client.Client;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.MediaType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ingest.ConfigurationUtils;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.utils.MapUtils;
import org.opensearch.ml.utils.SearchResponseUtil;
import org.opensearch.search.SearchHit;
import org.opensearch.search.pipeline.AbstractProcessor;
import org.opensearch.search.pipeline.PipelineProcessingContext;
import org.opensearch.search.pipeline.Processor;
import org.opensearch.search.pipeline.SearchResponseProcessor;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

public class MLInferenceSearchResponseProcessor extends AbstractProcessor implements SearchResponseProcessor, ModelExecutor {

    private final NamedXContentRegistry xContentRegistry;
    private static final Logger logger = LogManager.getLogger(MLInferenceSearchResponseProcessor.class);
    private final InferenceProcessorAttributes inferenceProcessorAttributes;
    private final boolean ignoreMissing;
    private final String functionName;
    private final boolean override;
    private final boolean fullResponsePath;
    private final boolean oneToOne;
    private final boolean ignoreFailure;
    private final String modelInput;
    private static Client client;
    public static final String TYPE = "ml_inference";
    // allow to ignore a field from mapping is not present in the query, and when the output field is not found in the
    // prediction outcomes, return the whole prediction outcome by skipping filtering
    public static final String IGNORE_MISSING = "ignore_missing";
    public static final String FUNCTION_NAME = "function_name";
    public static final String FULL_RESPONSE_PATH = "full_response_path";
    public static final String MODEL_INPUT = "model_input";
    public static final String ONE_TO_ONE = "one_to_one";
    public static final String DEFAULT_MODEL_INPUT = "{ \"parameters\": ${ml_inference.parameters} }";
    // At default, ml inference processor allows maximum 10 prediction tasks running in parallel
    // it can be overwritten using max_prediction_tasks when creating processor
    public static final int DEFAULT_MAX_PREDICTION_TASKS = 10;
    public static final String DEFAULT_OUTPUT_FIELD_NAME = "inference_results";

    protected MLInferenceSearchResponseProcessor(
        String modelId,
        List<Map<String, String>> inputMaps,
        List<Map<String, String>> outputMaps,
        Map<String, String> modelConfigMaps,
        int maxPredictionTask,
        String tag,
        String description,
        boolean ignoreMissing,
        String functionName,
        boolean fullResponsePath,
        boolean ignoreFailure,
        boolean override,
        String modelInput,
        Client client,
        NamedXContentRegistry xContentRegistry,
        boolean oneToOne
    ) {
        super(tag, description, ignoreFailure);
        this.oneToOne = oneToOne;
        this.inferenceProcessorAttributes = new InferenceProcessorAttributes(
            modelId,
            inputMaps,
            outputMaps,
            modelConfigMaps,
            maxPredictionTask
        );
        this.ignoreMissing = ignoreMissing;
        this.functionName = functionName;
        this.fullResponsePath = fullResponsePath;
        this.ignoreFailure = ignoreFailure;
        this.override = override;
        this.modelInput = modelInput;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
    }

    @Override
    public SearchResponse processResponse(SearchRequest request, SearchResponse response) throws Exception {
        throw new RuntimeException("ML inference search response processor make asynchronous calls and does not call processRequest");
    }

    /**
     * Processes the search response asynchronously by rewriting the documents with the inference results.
     *
     * By default, it processes multiple documents in a single prediction through the rewriteResponseDocuments method.
     * However, when processing one document per inference, it separates the N-hits search response into N one-hit search responses,
     * executes the same rewriteResponseDocument method for each one-hit search response,
     * and after receiving N one-hit search responses with inference results,
     * it combines them back into a single N-hits search response.
     *
     * @param request          the search request
     * @param response         the search response
     * @param responseContext  the pipeline processing context
     * @param responseListener the listener to be notified when the response is processed
     */
    @Override
    public void processResponseAsync(
        SearchRequest request,
        SearchResponse response,
        PipelineProcessingContext responseContext,
        ActionListener<SearchResponse> responseListener
    ) {
        try {
            SearchHit[] hits = response.getHits().getHits();
            // skip processing when there is no hit
            if (hits.length == 0) {
                responseListener.onResponse(response);
                return;
            }

            // if many to one, run rewriteResponseDocuments
            if (!oneToOne) {
                rewriteResponseDocuments(response, responseListener);
            } else {
                // if one to one, make one hit search response and run rewriteResponseDocuments
                GroupedActionListener<SearchResponse> combineResponseListener = getCombineResponseGroupedActionListener(
                    response,
                    responseListener,
                    hits
                );
                AtomicBoolean isOneHitListenerFailed = new AtomicBoolean(false);
                ;
                for (SearchHit hit : hits) {
                    SearchHit[] newHits = new SearchHit[1];
                    newHits[0] = hit;
                    SearchResponse oneHitResponse = SearchResponseUtil.replaceHits(newHits, response);
                    ActionListener<SearchResponse> oneHitListener = getOneHitListener(combineResponseListener, isOneHitListenerFailed);
                    rewriteResponseDocuments(oneHitResponse, oneHitListener);
                    // if any OneHitListener failure, try stop the rest of the predictions
                    if (isOneHitListenerFailed.get()) {
                        break;
                    }
                }
            }

        } catch (Exception e) {
            if (ignoreFailure) {
                responseListener.onResponse(response);
            } else {
                responseListener.onFailure(e);
                if (e instanceof OpenSearchStatusException) {
                    responseListener
                        .onFailure(
                            new OpenSearchStatusException(
                                "Failed to process response: " + e.getMessage(),
                                RestStatus.fromCode(((OpenSearchStatusException) e).status().getStatus())
                            )
                        );
                } else if (e instanceof MLResourceNotFoundException) {
                    responseListener
                        .onFailure(new OpenSearchStatusException("Failed to process response: " + e.getMessage(), RestStatus.NOT_FOUND));
                } else {
                    responseListener.onFailure(e);
                }
            }
        }
    }

    /**
     * Creates an ActionListener for a single SearchResponse that delegates its
     * onResponse and onFailure callbacks to a GroupedActionListener.
     *
     * @param combineResponseListener The GroupedActionListener to which the
     *                                onResponse and onFailure callbacks will be
     *                                delegated.
     * @param isOneHitListenerFailed
     * @return An ActionListener that delegates its callbacks to the provided
     * GroupedActionListener.
     */
    private static ActionListener<SearchResponse> getOneHitListener(
        GroupedActionListener<SearchResponse> combineResponseListener,
        AtomicBoolean isOneHitListenerFailed
    ) {
        ActionListener<SearchResponse> oneHitListener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse response) {
                combineResponseListener.onResponse(response);
            }

            @Override
            public void onFailure(Exception e) {
                // if any OneHitListener failure, try stop the rest of the predictions and return
                isOneHitListenerFailed.compareAndSet(false, true);
                combineResponseListener.onFailure(e);
            }
        };
        return oneHitListener;
    }

    /**
     * Creates a GroupedActionListener that combines the SearchResponses from individual hits
     * and constructs a new SearchResponse with the combined hits.
     *
     * @param response         The original SearchResponse containing the hits to be processed.
     * @param responseListener The ActionListener to be notified with the combined SearchResponse.
     * @param hits             The array of SearchHits to be processed.
     * @return A GroupedActionListener that combines the SearchResponses and constructs a new SearchResponse.
     */
    private GroupedActionListener<SearchResponse> getCombineResponseGroupedActionListener(
        SearchResponse response,
        ActionListener<SearchResponse> responseListener,
        SearchHit[] hits
    ) {
        GroupedActionListener<SearchResponse> combineResponseListener = new GroupedActionListener<>(new ActionListener<>() {
            @Override
            public void onResponse(Collection<SearchResponse> responseMapCollection) {
                SearchHit[] combinedHits = new SearchHit[hits.length];
                int i = 0;
                for (SearchResponse OneHitResponseAfterInference : responseMapCollection) {
                    SearchHit[] hitsAfterInference = OneHitResponseAfterInference.getHits().getHits();
                    combinedHits[i] = hitsAfterInference[0];
                    i++;
                }
                SearchResponse oneToOneInferenceSearchResponse = SearchResponseUtil.replaceHits(combinedHits, response);
                responseListener.onResponse(oneToOneInferenceSearchResponse);
            }

            @Override
            public void onFailure(Exception e) {
                if (ignoreFailure) {
                    responseListener.onResponse(response);
                } else {
                    responseListener.onFailure(e);
                }
            }
        }, hits.length);
        return combineResponseListener;
    }

    /**
     * Rewrite the documents in the search response with the inference results.
     *
     * @param response         the search response
     * @param responseListener the listener to be notified when the response is processed
     * @throws IOException if an I/O error occurs during the rewriting process
     */
    private void rewriteResponseDocuments(SearchResponse response, ActionListener<SearchResponse> responseListener) throws IOException {
        List<Map<String, String>> processInputMap = inferenceProcessorAttributes.getInputMaps();
        List<Map<String, String>> processOutputMap = inferenceProcessorAttributes.getOutputMaps();
        int inputMapSize = (processInputMap == null) ? 0 : processInputMap.size();

        // hitCountInPredictions keeps track of the count of hit that have the required input fields for each round of prediction
        Map<Integer, Integer> hitCountInPredictions = new HashMap<>();

        ActionListener<Map<Integer, MLOutput>> rewriteResponseListener = createRewriteResponseListener(
            response,
            responseListener,
            processInputMap,
            processOutputMap,
            hitCountInPredictions
        );

        GroupedActionListener<Map<Integer, MLOutput>> batchPredictionListener = createBatchPredictionListener(
            rewriteResponseListener,
            inputMapSize
        );
        SearchHit[] hits = response.getHits().getHits();
        for (int inputMapIndex = 0; inputMapIndex < max(inputMapSize, 1); inputMapIndex++) {
            processPredictions(hits, processInputMap, inputMapIndex, batchPredictionListener, hitCountInPredictions);
        }
    }

    /**
     * Processes the predictions for the given input map index.
     *
     * @param hits                    the search hits
     * @param processInputMap         the list of input mappings
     * @param inputMapIndex           the index of the input mapping to process
     * @param batchPredictionListener the listener to be notified when the predictions are processed
     * @param hitCountInPredictions   a map to keep track of the count of hits that have the required input fields for each round of prediction
     * @throws IOException if an I/O error occurs during the prediction process
     */
    private void processPredictions(
        SearchHit[] hits,
        List<Map<String, String>> processInputMap,
        int inputMapIndex,
        GroupedActionListener<Map<Integer, MLOutput>> batchPredictionListener,
        Map<Integer, Integer> hitCountInPredictions
    ) throws IOException {

        Map<String, String> modelParameters = new HashMap<>();
        Map<String, String> modelConfigs = new HashMap<>();

        if (inferenceProcessorAttributes.getModelConfigMaps() != null) {
            modelParameters.putAll(inferenceProcessorAttributes.getModelConfigMaps());
            modelConfigs.putAll(inferenceProcessorAttributes.getModelConfigMaps());
        }

        Map<String, Object> modelInputParameters = new HashMap<>();

        Map<String, String> inputMapping;
        if (processInputMap != null && !processInputMap.isEmpty()) {
            inputMapping = processInputMap.get(inputMapIndex);

            for (SearchHit hit : hits) {
                Map<String, Object> document = hit.getSourceAsMap();
                boolean isModelInputMissing = checkIsModelInputMissing(document, inputMapping);
                if (!isModelInputMissing) {
                    MapUtils.incrementCounter(hitCountInPredictions, inputMapIndex);
                    for (Map.Entry<String, String> entry : inputMapping.entrySet()) {
                        // model field as key, document field name as value
                        String modelInputFieldName = entry.getKey();
                        String documentFieldName = entry.getValue();

                        Object documentJson = JsonPath.parse(document).read("$");
                        Configuration configuration = Configuration
                            .builder()
                            .options(Option.SUPPRESS_EXCEPTIONS, Option.DEFAULT_PATH_LEAF_TO_NULL)
                            .build();

                        Object documentValue = JsonPath.using(configuration).parse(documentJson).read(documentFieldName);
                        if (documentValue != null) {
                            // when not existed in the map, add into the modelInputParameters map
                            updateModelInputParameters(modelInputParameters, modelInputFieldName, documentValue);
                        }
                    }
                } else { // when document does not contain the documentFieldName, skip when ignoreMissing
                    if (!ignoreMissing) {
                        throw new IllegalArgumentException(
                            "cannot find all required input fields: " + inputMapping.values() + " in hit:" + hit
                        );
                    }
                }
            }
        } else {
            for (SearchHit hit : hits) {
                Map<String, Object> document = hit.getSourceAsMap();
                MapUtils.incrementCounter(hitCountInPredictions, inputMapIndex);
                for (Map.Entry<String, Object> entry : document.entrySet()) {
                    // model field as key, document field name as value
                    String modelInputFieldName = entry.getKey();
                    Object documentValue = entry.getValue();

                    // when not existed in the map, add into the modelInputParameters map
                    updateModelInputParameters(modelInputParameters, modelInputFieldName, documentValue);
                }
            }
        }
        Map<String, String> modelParametersInString = StringUtils.getParameterMap(modelInputParameters);
        modelParameters.putAll(modelParametersInString);

        Set<String> inputMapKeys = new HashSet<>(modelParameters.keySet());
        inputMapKeys.removeAll(modelConfigs.keySet());

        Map<String, String> inputMappings = new HashMap<>();
        for (String k : inputMapKeys) {
            inputMappings.put(k, modelParameters.get(k));
        }

        ActionRequest request = getMLModelInferenceRequest(
            xContentRegistry,
            modelParameters,
            modelConfigs,
            inputMappings,
            inferenceProcessorAttributes.getModelId(),
            functionName,
            modelInput
        );

        client.execute(MLPredictionTaskAction.INSTANCE, request, new ActionListener<>() {

            @Override
            public void onResponse(MLTaskResponse mlTaskResponse) {
                MLOutput mlOutput = mlTaskResponse.getOutput();
                Map<Integer, MLOutput> mlOutputMap = new HashMap<>();
                mlOutputMap.put(inputMapIndex, mlOutput);
                batchPredictionListener.onResponse(mlOutputMap);
            }

            @Override
            public void onFailure(Exception e) {
                batchPredictionListener.onFailure(e);
            }
        });
    }

    /**
     * Updates the model input parameters map with the given document value.
     * If the setting is one-to-one,
     * simply put the document value in the map
     * If the setting is many-to-one,
     * create a new list and add the document value
     * @param modelInputParameters The map containing the model input parameters.
     * @param modelInputFieldName The name of the model input field.
     * @param documentValue The value from the document that needs to be added to the model input parameters.
     */
    private void updateModelInputParameters(Map<String, Object> modelInputParameters, String modelInputFieldName, Object documentValue) {
        if (!this.oneToOne) {
            if (!modelInputParameters.containsKey(modelInputFieldName)) {
                List<Object> documentValueList = new ArrayList<>();
                documentValueList.add(documentValue);
                modelInputParameters.put(modelInputFieldName, documentValueList);
            } else {
                List<Object> valueList = ((List) modelInputParameters.get(modelInputFieldName));
                valueList.add(documentValue);
            }
        } else {
            modelInputParameters.put(modelInputFieldName, documentValue);
        }
    }

    /**
     * Creates a grouped action listener for batch predictions.
     *
     * @param rewriteResponseListener the listener to be notified when the response is rewritten
     * @param inputMapSize            the size of the input map
     * @return a grouped action listener for batch predictions
     */
    private GroupedActionListener<Map<Integer, MLOutput>> createBatchPredictionListener(
        ActionListener<Map<Integer, MLOutput>> rewriteResponseListener,
        int inputMapSize
    ) {
        return new GroupedActionListener<>(new ActionListener<>() {
            @Override
            public void onResponse(Collection<Map<Integer, MLOutput>> mlOutputMapCollection) {
                Map<Integer, MLOutput> mlOutputMaps = new HashMap<>();
                for (Map<Integer, MLOutput> mlOutputMap : mlOutputMapCollection) {
                    mlOutputMaps.putAll(mlOutputMap);
                }
                rewriteResponseListener.onResponse(mlOutputMaps);
            }

            @Override
            public void onFailure(Exception e) {
                logger.error("Prediction Failed:", e);
                rewriteResponseListener.onFailure(e);
            }
        }, max(inputMapSize, 1));
    }

    /**
     * Creates an action listener for rewriting the response with the inference results.
     *
     * @param response              the search response
     * @param responseListener      the listener to be notified when the response is processed
     * @param processInputMap       the list of input mappings
     * @param processOutputMap      the list of output mappings
     * @param hitCountInPredictions a map to keep track of the count of hits that have the required input fields for each round of prediction
     * @return an action listener for rewriting the response with the inference results
     */
    private ActionListener<Map<Integer, MLOutput>> createRewriteResponseListener(
        SearchResponse response,
        ActionListener<SearchResponse> responseListener,
        List<Map<String, String>> processInputMap,
        List<Map<String, String>> processOutputMap,
        Map<Integer, Integer> hitCountInPredictions
    ) {
        return new ActionListener<>() {
            @Override
            public void onResponse(Map<Integer, MLOutput> multipleMLOutputs) {
                try {
                    Map<Integer, Map<String, Integer>> writeOutputMapDocCounter = new HashMap<>();

                    for (SearchHit hit : response.getHits().getHits()) {
                        Map<String, Object> sourceAsMapWithInference = new HashMap<>();
                        if (hit.hasSource()) {
                            BytesReference sourceRef = hit.getSourceRef();
                            Tuple<? extends MediaType, Map<String, Object>> typeAndSourceMap = XContentHelper
                                .convertToMap(sourceRef, false, (MediaType) null);

                            Map<String, Object> sourceAsMap = typeAndSourceMap.v2();
                            sourceAsMapWithInference.putAll(sourceAsMap);
                            Map<String, Object> document = hit.getSourceAsMap();

                            for (Map.Entry<Integer, MLOutput> entry : multipleMLOutputs.entrySet()) {
                                Integer mappingIndex = entry.getKey();
                                MLOutput mlOutput = entry.getValue();

                                Map<String, String> inputMapping = getDefaultInputMapping(sourceAsMap, mappingIndex, processInputMap);
                                Map<String, String> outputMapping = getDefaultOutputMapping(mappingIndex, processOutputMap);

                                boolean isModelInputMissing = false;
                                if (processInputMap != null && !processInputMap.isEmpty()) {
                                    isModelInputMissing = checkIsModelInputMissing(document, inputMapping);
                                }
                                if (!isModelInputMissing) {
                                    // Iterate over outputMapping
                                    for (Map.Entry<String, String> outputMapEntry : outputMapping.entrySet()) {

                                        String newDocumentFieldName = outputMapEntry.getKey();
                                        String modelOutputFieldName = outputMapEntry.getValue();

                                        MapUtils.incrementCounter(writeOutputMapDocCounter, mappingIndex, modelOutputFieldName);

                                        Object modelOutputValue = getModelOutputValue(
                                            mlOutput,
                                            modelOutputFieldName,
                                            ignoreMissing,
                                            fullResponsePath
                                        );
                                        Object modelOutputValuePerDoc;
                                        if (modelOutputValue instanceof List
                                            && ((List) modelOutputValue).size() == hitCountInPredictions.get(mappingIndex)) {
                                            Object valuePerDoc = ((List) modelOutputValue)
                                                .get(MapUtils.getCounter(writeOutputMapDocCounter, mappingIndex, modelOutputFieldName));
                                            modelOutputValuePerDoc = valuePerDoc;
                                        } else {
                                            modelOutputValuePerDoc = modelOutputValue;
                                        }

                                        if (sourceAsMap.containsKey(newDocumentFieldName)) {
                                            if (override) {
                                                sourceAsMapWithInference.remove(newDocumentFieldName);
                                                sourceAsMapWithInference.put(newDocumentFieldName, modelOutputValuePerDoc);
                                            } else {
                                                logger
                                                    .debug(
                                                        "{} already exists in the search response hit. Skip processing this field.",
                                                        newDocumentFieldName
                                                    );
                                                // TODO when the response has the same field name, should it throw exception? currently,
                                                // ingest processor quietly skip it
                                            }
                                        } else {
                                            sourceAsMapWithInference.put(newDocumentFieldName, modelOutputValuePerDoc);
                                        }
                                    }
                                }
                            }
                            XContentBuilder builder = XContentBuilder.builder(typeAndSourceMap.v1().xContent());
                            builder.map(sourceAsMapWithInference);
                            hit.sourceRef(BytesReference.bytes(builder));

                        }
                    }
                } catch (Exception e) {
                    if (ignoreFailure) {
                        responseListener.onResponse(response);

                    } else {
                        responseListener.onFailure(e);
                    }
                }
                responseListener.onResponse(response);
            }

            @Override
            public void onFailure(Exception e) {
                if (ignoreFailure) {
                    logger.error("Failed in writing prediction outcomes to search response", e);
                    responseListener.onResponse(response);

                } else {
                    responseListener.onFailure(e);
                }
            }
        };
    }

    /**
     * Checks if the document is missing any of the required input fields specified in the input mapping.
     *
     * @param document     the document map
     * @param inputMapping the input mapping
     * @return true if the document is missing any of the required input fields, false otherwise
     */
    private boolean checkIsModelInputMissing(Map<String, Object> document, Map<String, String> inputMapping) {
        boolean isModelInputMissing = false;
        for (Map.Entry<String, String> inputMapEntry : inputMapping.entrySet()) {
            String oldDocumentFieldName = inputMapEntry.getValue();
            boolean checkSingleModelInputPresent = hasField(document, oldDocumentFieldName);
            if (!checkSingleModelInputPresent) {
                isModelInputMissing = true;
                break;
            }
        }
        return isModelInputMissing;
    }

    /**
     * Retrieves the default output mapping for a given mapping index.
     *
     * <p>If the provided processOutputMap is null or empty, a new HashMap is created with a default
     * output field name mapped to a JsonPath expression representing the root object ($) followed by
     * the default output field name.
     *
     * <p>If the processOutputMap is not null and not empty, the mapping at the specified mappingIndex
     * is returned.
     *
     * @param mappingIndex     the index of the mapping to retrieve from the processOutputMap
     * @param processOutputMap the list of output mappings, can be null or empty
     * @return a Map containing the output mapping, either the default mapping or the mapping at the
     * specified index
     */
    private static Map<String, String> getDefaultOutputMapping(Integer mappingIndex, List<Map<String, String>> processOutputMap) {
        Map<String, String> outputMapping;
        if (processOutputMap == null || processOutputMap.size() == 0) {
            outputMapping = new HashMap<>();
            outputMapping.put(DEFAULT_OUTPUT_FIELD_NAME, "$." + DEFAULT_OUTPUT_FIELD_NAME);
        } else {
            outputMapping = processOutputMap.get(mappingIndex);
        }
        return outputMapping;
    }

    /**
     * Retrieves the default input mapping for a given mapping index and source map.
     *
     * <p>If the provided processInputMap is null or empty, a new HashMap is created by extracting
     * key-value pairs from the sourceAsMap using StringUtils.getParameterMap().
     *
     * <p>If the processInputMap is not null and not empty, the mapping at the specified mappingIndex
     * is returned.
     *
     * @param sourceAsMap     the source map containing the input data
     * @param mappingIndex    the index of the mapping to retrieve from the processInputMap
     * @param processInputMap the list of input mappings, can be null or empty
     * @return a Map containing the input mapping, either the mapping extracted from sourceAsMap or
     * the mapping at the specified index
     */
    private static Map<String, String> getDefaultInputMapping(
        Map<String, Object> sourceAsMap,
        Integer mappingIndex,
        List<Map<String, String>> processInputMap
    ) {
        Map<String, String> inputMapping;

        if (processInputMap == null || processInputMap.size() == 0) {
            inputMapping = new HashMap<>();
            inputMapping.putAll(StringUtils.getParameterMap(sourceAsMap));
        } else {
            inputMapping = processInputMap.get(mappingIndex);
        }
        return inputMapping;
    }

    /**
     * Returns the type of the processor.
     *
     * @return the type of the processor as a string
     */
    @Override
    public String getType() {
        return TYPE;
    }

    /**
     * A factory class for creating instances of the MLInferenceSearchResponseProcessor.
     * This class implements the Processor.Factory interface for creating SearchResponseProcessor instances.
     */
    public static class Factory implements Processor.Factory<SearchResponseProcessor> {
        private final Client client;
        private final NamedXContentRegistry xContentRegistry;

        /**
         * Constructs a new instance of the Factory class.
         *
         * @param client           the Client instance to be used by the Factory
         * @param xContentRegistry the xContentRegistry instance to be used by the Factory
         */
        public Factory(Client client, NamedXContentRegistry xContentRegistry) {
            this.client = client;
            this.xContentRegistry = xContentRegistry;
        }

        /**
         * Creates a new instance of the MLInferenceSearchResponseProcessor.
         *
         * @param processorFactories a map of processor factories
         * @param processorTag       the tag of the processor
         * @param description        the description of the processor
         * @param ignoreFailure      a flag indicating whether to ignore failures or not
         * @param config             the configuration map for the processor
         * @param pipelineContext    the pipeline context
         * @return a new instance of the MLInferenceSearchResponseProcessor
         */
        @Override
        public MLInferenceSearchResponseProcessor create(
            Map<String, Processor.Factory<SearchResponseProcessor>> processorFactories,
            String processorTag,
            String description,
            boolean ignoreFailure,
            Map<String, Object> config,
            PipelineContext pipelineContext
        ) {
            String modelId = ConfigurationUtils.readStringProperty(TYPE, processorTag, config, MODEL_ID);
            Map<String, Object> modelConfigInput = ConfigurationUtils.readOptionalMap(TYPE, processorTag, config, MODEL_CONFIG);

            List<Map<String, String>> inputMaps = ConfigurationUtils.readOptionalList(TYPE, processorTag, config, INPUT_MAP);
            List<Map<String, String>> outputMaps = ConfigurationUtils.readOptionalList(TYPE, processorTag, config, OUTPUT_MAP);
            int maxPredictionTask = ConfigurationUtils
                .readIntProperty(TYPE, processorTag, config, MAX_PREDICTION_TASKS, DEFAULT_MAX_PREDICTION_TASKS);
            boolean ignoreMissing = ConfigurationUtils.readBooleanProperty(TYPE, processorTag, config, IGNORE_MISSING, false);
            String functionName = ConfigurationUtils
                .readStringProperty(TYPE, processorTag, config, FUNCTION_NAME, FunctionName.REMOTE.name());
            boolean override = ConfigurationUtils.readBooleanProperty(TYPE, processorTag, config, OVERRIDE, false);
            boolean oneToOne = ConfigurationUtils.readBooleanProperty(TYPE, processorTag, config, ONE_TO_ONE, false);

            String modelInput = ConfigurationUtils.readOptionalStringProperty(TYPE, processorTag, config, MODEL_INPUT);

            // if model input is not provided for remote models, use default value
            if (functionName.equalsIgnoreCase("remote")) {
                modelInput = (modelInput != null) ? modelInput : DEFAULT_MODEL_INPUT;
            } else if (modelInput == null) {
                // if model input is not provided for local models, throw exception since it is mandatory here
                throw new IllegalArgumentException("Please provide model input when using a local model in ML Inference Processor");
            }
            boolean defaultFullResponsePath = !functionName.equalsIgnoreCase(FunctionName.REMOTE.name());
            boolean fullResponsePath = ConfigurationUtils
                .readBooleanProperty(TYPE, processorTag, config, FULL_RESPONSE_PATH, defaultFullResponsePath);

            ignoreFailure = ConfigurationUtils
                .readBooleanProperty(TYPE, processorTag, config, ConfigurationUtils.IGNORE_FAILURE_KEY, false);

            // convert model config user input data structure to Map<String, String>
            Map<String, String> modelConfigMaps = null;
            if (modelConfigInput != null) {
                modelConfigMaps = StringUtils.getParameterMap(modelConfigInput);
            }
            // check if the number of prediction tasks exceeds max prediction tasks
            if (inputMaps != null && inputMaps.size() > maxPredictionTask) {
                throw new IllegalArgumentException(
                    "The number of prediction task setting in this process is "
                        + inputMaps.size()
                        + ". It exceeds the max_prediction_tasks of "
                        + maxPredictionTask
                        + ". Please reduce the size of input_map or increase max_prediction_tasks."
                );
            }
            if (outputMaps != null && inputMaps != null && outputMaps.size() != inputMaps.size()) {
                throw new IllegalArgumentException(
                    "when output_maps and input_maps are provided, their length needs to match. The input_maps is in length of "
                        + inputMaps.size()
                        + ", while output_maps is in the length of "
                        + outputMaps.size()
                        + ". Please adjust mappings."
                );
            }

            return new MLInferenceSearchResponseProcessor(
                modelId,
                inputMaps,
                outputMaps,
                modelConfigMaps,
                maxPredictionTask,
                processorTag,
                description,
                ignoreMissing,
                functionName,
                fullResponsePath,
                ignoreFailure,
                override,
                modelInput,
                client,
                xContentRegistry,
                oneToOne
            );
        }
    }

}
