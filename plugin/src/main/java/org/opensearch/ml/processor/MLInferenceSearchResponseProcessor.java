/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.processor;

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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.GroupedActionListener;
import org.opensearch.client.Client;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.MediaType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ingest.ConfigurationUtils;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.utils.VersionedMapUtils;
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
    public static final String DEFAULT_MODEl_INPUT = "{ \"parameters\": ${ml_inference.parameters} }";
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
        NamedXContentRegistry xContentRegistry
    ) {
        super(tag, description, ignoreFailure);
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
            rewriteResponseDocuments(response, hits, responseListener);
        } catch (Exception e) {
            responseListener.onFailure(e);
        }
    }

    private void rewriteResponseDocuments(SearchResponse response, SearchHit[] hits, ActionListener<SearchResponse> responseListener)
        throws IOException {
        List<Map<String, String>> processInputMap = inferenceProcessorAttributes.getInputMaps();
        List<Map<String, String>> processOutputMap = inferenceProcessorAttributes.getOutputMaps();
        int inputMapSize = (processInputMap != null) ? processInputMap.size() : 0;

        // TODO decide the default mapping
        if (inputMapSize == 0) {
            responseListener.onResponse(response);
            return;
        }

        ActionListener<Map<Integer, MLOutput>> rewriteResponseListener = createRewriteRequestListener(
            response,
            responseListener,
            processInputMap,
            processOutputMap
        );

        GroupedActionListener<Map<Integer, MLOutput>> batchPredictionListener = createBatchPredictionListener(
            rewriteResponseListener,
            inputMapSize
        );

        for (int inputMapIndex = 0; inputMapIndex < inputMapSize; inputMapIndex++) {
            processPredictions(response, hits, processInputMap, inputMapIndex, batchPredictionListener);
        }
    }

    private void processPredictions(
        SearchResponse response,
        SearchHit[] hits,
        List<Map<String, String>> processInputMap,
        int inputMapIndex,
        GroupedActionListener<Map<Integer, MLOutput>> batchPredictionListener
    ) throws IOException {

        Map<String, String> modelParameters = new HashMap<>();
        Map<String, String> modelConfigs = new HashMap<>();

        if (inferenceProcessorAttributes.getModelConfigMaps() != null) {
            modelParameters.putAll(inferenceProcessorAttributes.getModelConfigMaps());
            modelConfigs.putAll(inferenceProcessorAttributes.getModelConfigMaps());
        }

        Map<String, Object> modelInputParameters = new HashMap<>();

        Map<String, String> inputMapping;
        if (processInputMap != null) {
            inputMapping = processInputMap.get(inputMapIndex);
            for (SearchHit hit : hits) {
                for (Map.Entry<String, String> entry : inputMapping.entrySet()) {
                    // model field as key, document field name as value
                    String modelInputFieldName = entry.getKey();
                    String documentFieldName = entry.getValue();

                    Map<String, Object> document = hit.getSourceAsMap();
                    Object documentJson = JsonPath.parse(document).read("$");
                    Configuration configuration = Configuration
                        .builder()
                        .options(Option.SUPPRESS_EXCEPTIONS, Option.DEFAULT_PATH_LEAF_TO_NULL)
                        .build();

                    Object documentValue = JsonPath.using(configuration).parse(documentJson).read(documentFieldName);
                    if (documentValue != null) {
                        // when not existed in the map, add into the modelInputParameters map
                        if (!modelInputParameters.containsKey(modelInputFieldName)) {
                            modelInputParameters.put(modelInputFieldName, documentValue);
                        } else {
                            if (modelInputParameters.get(modelInputFieldName) instanceof List) {
                                List<Object> valueList = ((List) modelInputParameters.get(modelInputFieldName));
                                valueList.add(documentValue);
                            } else {
                                Object firstValue = modelInputParameters.remove(modelInputFieldName);
                                List<Object> documentValueList = new ArrayList<>();
                                documentValueList.add(firstValue);
                                documentValueList.add(documentValue);
                                modelInputParameters.put(modelInputFieldName, documentValueList);
                            }
                        }

                    }
                    // when document does not contain the documentFieldName, skip when ignoreMissing
                    else {
                        if (!ignoreMissing) {
                            throw new IllegalArgumentException("cannot find field name: " + documentFieldName + " in hit:" + hit);
                        }

                    }
                }
            }
            for (Map.Entry<String, Object> entry : modelInputParameters.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                modelParameters.put(key, StringUtils.toJson(value));
            }

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

    }

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
        }, Math.max(inputMapSize, 1));
    }

    private ActionListener<Map<Integer, MLOutput>> createRewriteRequestListener(
        SearchResponse response,
        ActionListener<SearchResponse> responseListener,
        List<Map<String, String>> processInputMap,
        List<Map<String, String>> processOutputMap
    ) {
        return new ActionListener<>() {
            @Override
            public void onResponse(Map<Integer, MLOutput> multipleMLOutputs) {
                try {

                    Map<Integer, Map<String, Integer>> hasInputMapFieldDocCounter = new HashMap<>();

                    for (SearchHit hit : response.getHits().getHits()) {
                        Map<String, Object> sourceAsMapWithInference = new HashMap<>();
                        if (hit.hasSource()) {
                            BytesReference sourceRef = hit.getSourceRef();
                            Tuple<? extends MediaType, Map<String, Object>> typeAndSourceMap = XContentHelper
                                .convertToMap(sourceRef, false, (MediaType) null);

                            Map<String, Object> sourceAsMap = typeAndSourceMap.v2();
                            sourceAsMapWithInference.putAll(sourceAsMap);
                            for (Map.Entry<Integer, MLOutput> entry : multipleMLOutputs.entrySet()) {
                                Integer mappingIndex = entry.getKey();
                                MLOutput mlOutput = entry.getValue();
                                Map<String, String> outputMapping = processOutputMap.get(mappingIndex);
                                Map<String, String> inputMapping = processInputMap.get(mappingIndex);

                                // TODO deal with no inputMapping and no outputMapping edge case.
                                Iterator<Map.Entry<String, String>> inputIterator = inputMapping.entrySet().iterator();
                                Iterator<Map.Entry<String, String>> outputIterator = outputMapping.entrySet().iterator();

                                // Iterate over both maps simultaneously
                                while (inputIterator.hasNext() || outputIterator.hasNext()) {
                                    Map.Entry<String, String> inputMapEntry = inputIterator.hasNext() ? inputIterator.next() : null;
                                    Map.Entry<String, String> outputMapEntry = outputIterator.hasNext() ? outputIterator.next() : null;
                                    String modelInputFieldName = inputMapEntry.getKey();
                                    String oldDocumentFieldName = inputMapEntry.getValue();

                                    Map<String, Object> document = hit.getSourceAsMap();
                                    if (hasField(document, oldDocumentFieldName)) {

                                        VersionedMapUtils.incrementCounter(hasInputMapFieldDocCounter, mappingIndex, modelInputFieldName);

                                        String newDocumentFieldName = outputMapEntry.getKey();
                                        String modelOutputFieldName = outputMapEntry.getValue();
                                        Object modelOutputValue = getModelOutputValue(
                                            mlOutput,
                                            modelOutputFieldName,
                                            ignoreMissing,
                                            fullResponsePath
                                        );
                                        Object modelOutputValuePerDoc;
                                        if (modelOutputValue instanceof List && ((List) modelOutputValue).size() > 1) {
                                            Object valuePerDoc = ((List) modelOutputValue)
                                                .get(
                                                    VersionedMapUtils
                                                        .getCounter(hasInputMapFieldDocCounter, mappingIndex, modelInputFieldName)
                                                );
                                            modelOutputValuePerDoc = valuePerDoc;
                                        } else {
                                            modelOutputValuePerDoc = modelOutputValue;
                                        }

                                        if (sourceAsMap.containsKey(newDocumentFieldName)) {
                                            if (override) {
                                                sourceAsMapWithInference.remove(newDocumentFieldName);
                                                sourceAsMapWithInference.put(newDocumentFieldName, modelOutputValuePerDoc);
                                            }
                                        } else {
                                            sourceAsMapWithInference.put(newDocumentFieldName, modelOutputValuePerDoc);
                                        }
                                    } else {
                                        if (!ignoreMissing) {
                                            throw new IllegalArgumentException(
                                                "cannot find field name: " + oldDocumentFieldName + " in hit:" + hit
                                            );
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
                    logger.error("Failed in writing prediction outcomes to new query", e);
                    responseListener.onResponse(response);

                } else {
                    responseListener.onFailure(e);
                }
            }
        };
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

            List<Map<String, String>> inputMaps = ConfigurationUtils.readList(TYPE, processorTag, config, INPUT_MAP);
            List<Map<String, String>> outputMaps = ConfigurationUtils.readList(TYPE, processorTag, config, OUTPUT_MAP);
            int maxPredictionTask = ConfigurationUtils
                .readIntProperty(TYPE, processorTag, config, MAX_PREDICTION_TASKS, DEFAULT_MAX_PREDICTION_TASKS);
            boolean ignoreMissing = ConfigurationUtils.readBooleanProperty(TYPE, processorTag, config, IGNORE_MISSING, false);
            String functionName = ConfigurationUtils
                .readStringProperty(TYPE, processorTag, config, FUNCTION_NAME, FunctionName.REMOTE.name());
            boolean override = ConfigurationUtils.readBooleanProperty(TYPE, processorTag, config, OVERRIDE, false);

            String modelInput = ConfigurationUtils.readOptionalStringProperty(TYPE, processorTag, config, MODEL_INPUT);

            // if model input is not provided for remote models, use default value
            if (functionName.equalsIgnoreCase("remote")) {
                modelInput = (modelInput != null) ? modelInput : DEFAULT_MODEl_INPUT;
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
                    "when output_maps and input_maps are provided, their length needs to match. The input_maps is in length of"
                        + inputMaps.size()
                        + ", while output_maps is in the length of"
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
                xContentRegistry
            );
        }
    }

}
