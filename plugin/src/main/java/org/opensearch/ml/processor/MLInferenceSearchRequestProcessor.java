/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.processor;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.utils.StringUtils.toJson;
import static org.opensearch.ml.processor.InferenceProcessorAttributes.INPUT_MAP;
import static org.opensearch.ml.processor.InferenceProcessorAttributes.MAX_PREDICTION_TASKS;
import static org.opensearch.ml.processor.InferenceProcessorAttributes.MODEL_CONFIG;
import static org.opensearch.ml.processor.InferenceProcessorAttributes.MODEL_ID;
import static org.opensearch.ml.processor.InferenceProcessorAttributes.OUTPUT_MAP;
import static org.opensearch.ml.processor.ModelExecutor.combineMaps;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.text.StringSubstitutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.support.GroupedActionListener;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ingest.ConfigurationUtils;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.pipeline.AbstractProcessor;
import org.opensearch.search.pipeline.PipelineProcessingContext;
import org.opensearch.search.pipeline.Processor;
import org.opensearch.search.pipeline.SearchRequestProcessor;
import org.opensearch.transport.client.Client;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.ReadContext;

import lombok.Getter;

/**
 * MLInferenceSearchRequestProcessor requires a modelId string to call model inferences
 * maps fields from query string for model input, and maps model inference output to the query strings or query template
 * this processor also handles dot path notation for nested object( map of array) by rewriting json path accordingly
 */
public class MLInferenceSearchRequestProcessor extends AbstractProcessor implements SearchRequestProcessor, ModelExecutor {
    private final NamedXContentRegistry xContentRegistry;
    private static final Logger logger = LogManager.getLogger(MLInferenceSearchRequestProcessor.class);
    @Getter
    private final InferenceProcessorAttributes inferenceProcessorAttributes;
    private final boolean ignoreMissing;
    private final String functionName;
    @Getter
    private final List<Map<String, String>> optionalInputMaps;
    @Getter
    private final List<Map<String, String>> optionalOutputMaps;
    private String queryTemplate;
    private final boolean fullResponsePath;
    private final boolean ignoreFailure;
    private final String modelInput;
    private static Client client;
    public static final String TYPE = "ml_inference";
    // allow to ignore a field from mapping is not present in the query, and when the output field is not found in the
    // prediction outcomes, return the whole prediction outcome by skipping filtering
    public static final String IGNORE_MISSING = "ignore_missing";
    public static final String QUERY_TEMPLATE = "query_template";
    public static final String FUNCTION_NAME = "function_name";
    public static final String FULL_RESPONSE_PATH = "full_response_path";
    public static final String MODEL_INPUT = "model_input";
    public static final String DEFAULT_MODEl_INPUT = "{ \"parameters\": ${ml_inference.parameters} }";
    // At default, ml inference processor allows maximum 10 prediction tasks running in parallel
    // it can be overwritten using max_prediction_tasks when creating processor
    public static final int DEFAULT_MAX_PREDICTION_TASKS = 10;
    public static final String OPTIONAL_INPUT_MAP = "optional_input_map";
    public static final String OPTIONAL_OUTPUT_MAP = "optional_output_map";

    protected MLInferenceSearchRequestProcessor(
        String modelId,
        String queryTemplate,
        List<Map<String, String>> inputMaps,
        List<Map<String, String>> outputMaps,
        List<Map<String, String>> optionalInputMaps,
        List<Map<String, String>> optionalOutputMaps,
        Map<String, String> modelConfigMaps,
        int maxPredictionTask,
        String tag,
        String description,
        boolean ignoreMissing,
        String functionName,
        boolean fullResponsePath,
        boolean ignoreFailure,
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
        this.optionalInputMaps = optionalInputMaps;
        this.optionalOutputMaps = optionalOutputMaps;
        this.ignoreMissing = ignoreMissing;
        this.functionName = functionName;
        this.fullResponsePath = fullResponsePath;
        this.queryTemplate = queryTemplate;
        this.ignoreFailure = ignoreFailure;
        this.modelInput = modelInput;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
    }

    /**
     * Process a SearchRequest without receiving request-scoped state.
     * Implement this method if the processor makes no asynchronous calls.
     * But this processor is going to make asynchronous calls.
     * @param request the search request (which may have been modified by an earlier processor)
     * @return the modified search request
     * @throws Exception implementation-specific processing exception
     */

    @Override
    public SearchRequest processRequest(SearchRequest request) throws Exception {
        throw new RuntimeException("ML inference search request processor make asynchronous calls and does not call processRequest");
    }

    /**
     * Transform a {@link SearchRequest}.
     * Make one or more predictions, rewrite query in a search request
     * Executed on the coordinator node before any {@link org.opensearch.action.search.SearchPhase}
     * executes.
     * <p>
     * Expert method: Implement this if the processor needs to make asynchronous calls. Otherwise, implement processRequest.
     * @param request the executed {@link SearchRequest}
     * @param requestListener callback to be invoked on successful processing or on failure
     */
    @Override
    public void processRequestAsync(
        SearchRequest request,
        PipelineProcessingContext requestContext,
        ActionListener<SearchRequest> requestListener
    ) {

        try {
            if (request.source() == null) {
                throw new IllegalArgumentException("query body is empty, cannot processor inference on empty query request.");
            }
            setRequestContextFromExt(request, requestContext);
            String queryString = request.source().toString();
            rewriteQueryString(request, queryString, requestListener, requestContext);

        } catch (Exception e) {
            if (ignoreFailure) {
                requestListener.onResponse(request);
            } else {
                requestListener.onFailure(e);
            }
        }
    }

    /**
     * Rewrites the query string based on the input and output mappings and the ML model output.
     *
     * @param request         the {@link SearchRequest} to be rewritten
     * @param queryString     the original query string
     * @param requestListener the {@link ActionListener} to be notified when the rewriting is complete
     * @param requestContext
     * @throws IOException if an I/O error occurs during the rewriting process
     */
    private void rewriteQueryString(
        SearchRequest request,
        String queryString,
        ActionListener<SearchRequest> requestListener,
        PipelineProcessingContext requestContext
    ) throws IOException {
        List<Map<String, String>> processInputMap = inferenceProcessorAttributes.getInputMaps();
        List<Map<String, String>> processOutputMap = inferenceProcessorAttributes.getOutputMaps();

        // Combine processInputMap and optionalInputMaps
        List<Map<String, String>> combinedInputMaps = combineMaps(processInputMap, optionalInputMaps);
        // Combine processOutputMap and optionalOutputMaps
        List<Map<String, String>> combinedOutputMaps = combineMaps(processOutputMap, optionalOutputMaps);

        int combinedInputMapSize = (combinedInputMaps != null) ? combinedInputMaps.size() : 0;

        if (combinedInputMapSize == 0) {
            requestListener.onResponse(request);
            return;
        }

        try {
            if (!validateQueryFieldInQueryString(processInputMap, processOutputMap, queryString, ignoreMissing)) {
                requestListener.onResponse(request);
            }
        } catch (Exception e) {
            if (ignoreFailure) {
                requestListener.onResponse(request);
                return;
            } else {
                requestListener.onFailure(e);
                return;
            }
        }

        ActionListener<Map<Integer, MLOutput>> rewriteRequestListener = createRewriteRequestListener(
            request,
            queryString,
            requestListener,
            combinedOutputMaps,
            requestContext
        );
        GroupedActionListener<Map<Integer, MLOutput>> batchPredictionListener = createBatchPredictionListener(
            rewriteRequestListener,
            combinedInputMapSize
        );

        for (int inputMapIndex = 0; inputMapIndex < combinedInputMapSize; inputMapIndex++) {
            processPredictions(queryString, combinedInputMaps, inputMapIndex, batchPredictionListener);
        }

    }

    /**
     * Creates an {@link ActionListener} that handles the response from the ML model inference and updates the query string
     * or query template with the model output.
     *
     * @param request          the {@link SearchRequest} to be updated
     * @param queryString      the original query string
     * @param requestListener  the {@link ActionListener} to be notified when the query string or query template is updated
     * @param processOutputMap the list of output mappings
     * @param requestContext   the requestContext can be carried over search processors into the search pipeline
     * @return an {@link ActionListener} that handles the response from the ML model inference
     */
    private ActionListener<Map<Integer, MLOutput>> createRewriteRequestListener(
        SearchRequest request,
        String queryString,
        ActionListener<SearchRequest> requestListener,
        List<Map<String, String>> processOutputMap,
        PipelineProcessingContext requestContext
    ) {
        return new ActionListener<>() {
            @Override
            public void onResponse(Map<Integer, MLOutput> multipleMLOutputs) {
                for (Map.Entry<Integer, MLOutput> entry : multipleMLOutputs.entrySet()) {
                    Integer mappingIndex = entry.getKey();
                    MLOutput mlOutput = entry.getValue();
                    Map<String, String> outputMapping = processOutputMap.get(mappingIndex);
                    try {
                        if (queryTemplate == null) {
                            Object incomeQueryObject = JsonPath.parse(queryString).read("$");
                            updateIncomeQueryObject(incomeQueryObject, outputMapping, mlOutput, requestContext);
                            SearchSourceBuilder searchSourceBuilder = getSearchSourceBuilder(xContentRegistry, toJson(incomeQueryObject));
                            request.source(searchSourceBuilder);

                            requestListener.onResponse(request);
                        } else {
                            String newQueryString = updateQueryTemplate(queryTemplate, outputMapping, mlOutput);
                            SearchSourceBuilder searchSourceBuilder = getSearchSourceBuilder(xContentRegistry, newQueryString);
                            request.source(searchSourceBuilder);
                            requestListener.onResponse(request);
                        }
                    } catch (Exception e) {
                        if (ignoreFailure) {
                            logger.error("Failed in writing prediction outcomes to new query", e);
                            requestListener.onResponse(request);

                        } else {
                            requestListener.onFailure(e);
                        }
                    }
                }
            }

            @Override
            public void onFailure(Exception e) {
                if (ignoreFailure) {
                    logger.error("Failed in writing prediction outcomes to new query", e);
                    requestListener.onResponse(request);

                } else {
                    requestListener.onFailure(e);
                }
            }

            /**
             * Updates the income query object with values from the ML output based on the provided output mapping.
             *
             * This method iterates through the output mapping, retrieves corresponding values from the ML output,
             * and updates the income query object accordingly. It also handles nested JSON structures and updates
             * the request context with the new values.
             *
             * @param incomeQueryObject The object representing the income query to be updated.
             * @param outputMapping A map containing the mapping between new query fields and model output field names.
             * @param mlOutput The MLOutput object containing the results from the machine learning model.
             * @param requestContext The context object for the current pipeline processing request.
             *
             * @throws IllegalArgumentException If a specified JSON path cannot be found in the query string.
             *
             * @implNote This method uses JsonPath for JSON manipulation and supports both regular and extended (ext) fields.
             *           For extended fields, it creates nested structures if they don't exist.
             *           The method also updates the request context with new field values for further processing.
             *
             * @see JsonPath
             * @see PipelineProcessingContext
             * @see MLOutput
             */
            private void updateIncomeQueryObject(
                Object incomeQueryObject,
                Map<String, String> outputMapping,
                MLOutput mlOutput,
                PipelineProcessingContext requestContext
            ) {
                for (Map.Entry<String, String> outputMapEntry : outputMapping.entrySet()) {
                    String newQueryField = null;
                    try {
                        newQueryField = outputMapEntry.getKey();
                        String modelOutputFieldName = outputMapEntry.getValue();

                        // Check if transformation is needed
                        String baseFieldName = OutputTransformations.getBaseFieldName(modelOutputFieldName);
                        Object modelOutputValue = getModelOutputValue(mlOutput, baseFieldName, ignoreMissing, fullResponsePath);

                        // Apply transformation if specified
                        if (OutputTransformations.hasTransformation(modelOutputFieldName)) {
                            modelOutputValue = OutputTransformations.applyTransformation(modelOutputFieldName, modelOutputValue);
                        }

                        requestContext.setAttribute(newQueryField, modelOutputValue);

                        // if output mapping is using jsonpath starts with $. or use dot path starts with ext.
                        // to allow writing to search extension, try to prepare the path in the query,
                        // for example {"ext":{"ml_inference":{}}}
                        if (newQueryField.startsWith("$.ext.") || newQueryField.startsWith("ext.")) {
                            incomeQueryObject = StringUtils.prepareNestedStructures(incomeQueryObject, newQueryField);
                        }
                        if (StringUtils.pathExists(incomeQueryObject, newQueryField)) {
                            JsonPath.using(suppressExceptionConfiguration).parse(incomeQueryObject).set(newQueryField, modelOutputValue);
                        }

                    } catch (PathNotFoundException e) {
                        logger.error("Failed to set {} in query string: {}", newQueryField, e.getMessage(), e);
                        throw new IllegalArgumentException("can not find path " + newQueryField + "in query string");
                    }
                }
            }

            private String updateQueryTemplate(String queryTemplate, Map<String, String> outputMapping, MLOutput mlOutput) {
                Map<String, Object> valuesMap = new HashMap<>();
                for (Map.Entry<String, String> outputMapEntry : outputMapping.entrySet()) {
                    String newQueryField = outputMapEntry.getKey();
                    String modelOutputFieldName = outputMapEntry.getValue();

                    // Check if transformation is needed
                    String baseFieldName = OutputTransformations.getBaseFieldName(modelOutputFieldName);
                    Object modelOutputValue = getModelOutputValue(mlOutput, baseFieldName, ignoreMissing, fullResponsePath);

                    // Apply transformation if specified
                    if (OutputTransformations.hasTransformation(modelOutputFieldName)) {
                        modelOutputValue = OutputTransformations.applyTransformation(modelOutputFieldName, modelOutputValue);
                    }

                    if (modelOutputValue instanceof Map) {
                        modelOutputValue = toJson(modelOutputValue);
                    }
                    valuesMap.put(newQueryField, modelOutputValue);
                }
                StringSubstitutor sub = new StringSubstitutor(valuesMap);
                return sub.replace(queryTemplate);
            }
        };
    }

    /**
     * Creates a {@link GroupedActionListener} that collects the responses from multiple ML model inferences.
     *
     * @param rewriteRequestListener the {@link ActionListener} to be notified when all ML model inferences are complete
     * @param inputMapSize  the number of input mappings
     * @return a {@link GroupedActionListener} that handles the responses from multiple ML model inferences
     */
    private GroupedActionListener<Map<Integer, MLOutput>> createBatchPredictionListener(
        ActionListener<Map<Integer, MLOutput>> rewriteRequestListener,
        int inputMapSize
    ) {
        return new GroupedActionListener<>(new ActionListener<>() {
            @Override
            public void onResponse(Collection<Map<Integer, MLOutput>> mlOutputMapCollection) {
                Map<Integer, MLOutput> mlOutputMaps = new HashMap<>();
                for (Map<Integer, MLOutput> mlOutputMap : mlOutputMapCollection) {
                    mlOutputMaps.putAll(mlOutputMap);
                }
                rewriteRequestListener.onResponse(mlOutputMaps);
            }

            @Override
            public void onFailure(Exception e) {
                logger.error("Prediction Failed:", e);
                rewriteRequestListener.onFailure(e);
            }
        }, Math.max(inputMapSize, 1));
    }

    private boolean validateRequiredInputMappingFields(
        List<Map<String, String>> processInputMap,
        String queryString,
        boolean ignoreMissing
    ) {
        // Suppress errors thrown by JsonPath and instead return null if a path does not exist in a JSON blob.
        Configuration suppressExceptionConfiguration = Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS);
        ReadContext jsonData = JsonPath.using(suppressExceptionConfiguration).parse(queryString);

        // check all values if exists in query for required fields
        for (Map<String, String> inputMap : processInputMap) {
            for (Map.Entry<String, String> entry : inputMap.entrySet()) {
                // the inputMap takes in model input as keys and query fields as value
                String queryField = entry.getValue();
                Object pathData = jsonData.read(queryField);
                if (pathData == null) {
                    if (!ignoreMissing) {
                        throw new IllegalArgumentException(
                            "cannot find field: " + queryField + " in query string: " + jsonData.jsonString()
                        );
                    } else {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean validateRequiredOutputMappingFields(
        List<Map<String, String>> processOutputMap,
        String queryString,
        boolean ignoreMissing
    ) {
        // Suppress errors thrown by JsonPath and instead return null if a path does not exist in a JSON blob.
        Configuration suppressExceptionConfiguration = Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS);
        ReadContext jsonData = JsonPath.using(suppressExceptionConfiguration).parse(queryString);

        // check all values if exists in query for required fields
        if (queryTemplate == null) {
            for (Map<String, String> outputMap : processOutputMap) {
                for (Map.Entry<String, String> entry : outputMap.entrySet()) {
                    String queryField = entry.getKey();
                    // output writing to search extension can be new field
                    if (queryField.startsWith("query.") || queryField.startsWith("$.query.")) {
                        Object pathData = jsonData.read(queryField);
                        if (pathData == null) {
                            if (!ignoreMissing) {
                                throw new IllegalArgumentException(
                                    "cannot find field: " + queryField + " in query string: " + jsonData.jsonString()
                                );
                            } else {
                                return false;
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    /**
     * Validates that the query fields specified in the input and output mappings exist in the query string.
     *
     * @param processInputMap  the list of input mappings
     * @param processOutputMap the list of output mappings
     * @param queryString      the query string to be validated
     * @param ignoreMissing
     * @return true if all query fields exist in the query string, false otherwise
     */
    private boolean validateQueryFieldInQueryString(
        List<Map<String, String>> processInputMap,
        List<Map<String, String>> processOutputMap,
        String queryString,
        boolean ignoreMissing
    ) {
        if (!CollectionUtils.isEmpty(processInputMap)) {
            if (!validateRequiredInputMappingFields(processInputMap, queryString, ignoreMissing)) {
                return false;
            }
        }

        if (!CollectionUtils.isEmpty(processOutputMap)) {
            if (!validateRequiredOutputMappingFields(processOutputMap, queryString, ignoreMissing)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Processes the ML model inference for a given input mapping index.
     *
     * @param queryString             the original query string
     * @param processInputMap         the list of input mappings
     * @param inputMapIndex           the index of the input mapping to be processed
     * @param batchPredictionListener the {@link GroupedActionListener} to be notified when the ML model inference is complete
     * @throws IOException if an I/O error occurs during the processing
     */
    private void processPredictions(
        String queryString,
        List<Map<String, String>> processInputMap,
        int inputMapIndex,
        GroupedActionListener batchPredictionListener
    ) throws IOException {
        Map<String, String> modelParameters = new HashMap<>();
        Map<String, String> modelConfigs = new HashMap<>();

        if (inferenceProcessorAttributes.getModelConfigMaps() != null) {
            modelParameters.putAll(inferenceProcessorAttributes.getModelConfigMaps());
            modelConfigs.putAll(inferenceProcessorAttributes.getModelConfigMaps());
        }
        Map<String, String> inputMapping = new HashMap<>();

        if (processInputMap != null) {
            inputMapping = processInputMap.get(inputMapIndex);
            Object newQuery = JsonPath.parse(queryString).read("$");
            for (Map.Entry<String, String> entry : inputMapping.entrySet()) {
                // model field as key, query field name as value
                String modelInputFieldName = entry.getKey();
                String queryFieldName = entry.getValue();
                if (hasField(newQuery, queryFieldName)) {
                    String queryFieldValue = toJson(JsonPath.parse(newQuery).read(queryFieldName));
                    modelParameters.put(modelInputFieldName, queryFieldValue);
                }
            }
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

    /**
     * Creates a SearchSourceBuilder instance from the given query string.
     *
     * This method parses the provided query string, substitutes parameters, and constructs
     * a SearchSourceBuilder object. It handles JSON content and performs variable substitution
     * using a StringSubstitutor.
     *
     * @param xContentRegistry the XContentRegistry instance to be used for parsing
     * @param queryString      the query template string to be parsed
     * @return a SearchSourceBuilder instance created from the query string
     * @throws IOException if an I/O error occurs during parsing or content creation
     */
    private static SearchSourceBuilder getSearchSourceBuilder(NamedXContentRegistry xContentRegistry, String queryString)
        throws IOException {

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        XContentParser queryParser = XContentType.JSON
            .xContent()
            .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, queryString);
        ensureExpectedToken(XContentParser.Token.START_OBJECT, queryParser.nextToken(), queryParser);

        searchSourceBuilder.parseXContent(queryParser);

        return searchSourceBuilder;

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
     * A factory class for creating instances of the MLInferenceSearchRequestProcessor.
     * This class implements the Processor.Factory interface for creating SearchRequestProcessor instances.
     */
    public static class Factory implements Processor.Factory<SearchRequestProcessor> {
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
         * Creates a new instance of the MLInferenceSearchRequestProcessor.
         *
         * @param processorFactories a map of processor factories
         * @param processorTag       the tag of the processor
         * @param description        the description of the processor
         * @param ignoreFailure      a flag indicating whether to ignore failures or not
         * @param config             the configuration map for the processor
         * @param pipelineContext    the pipeline context
         * @return a new instance of the MLInferenceSearchRequestProcessor
         */
        @Override
        public MLInferenceSearchRequestProcessor create(
            Map<String, Processor.Factory<SearchRequestProcessor>> processorFactories,
            String processorTag,
            String description,
            boolean ignoreFailure,
            Map<String, Object> config,
            PipelineContext pipelineContext
        ) {
            String modelId = ConfigurationUtils.readStringProperty(TYPE, processorTag, config, MODEL_ID);
            String queryTemplate = ConfigurationUtils.readOptionalStringProperty(TYPE, processorTag, config, QUERY_TEMPLATE);
            Map<String, Object> modelConfigInput = ConfigurationUtils.readOptionalMap(TYPE, processorTag, config, MODEL_CONFIG);

            List<Map<String, String>> inputMaps = ConfigurationUtils.readOptionalList(TYPE, processorTag, config, INPUT_MAP);
            List<Map<String, String>> outputMaps = ConfigurationUtils.readOptionalList(TYPE, processorTag, config, OUTPUT_MAP);

            List<Map<String, String>> optionalInputMaps = ConfigurationUtils
                .readOptionalList(TYPE, processorTag, config, OPTIONAL_INPUT_MAP);
            List<Map<String, String>> optionalOutputMaps = ConfigurationUtils
                .readOptionalList(TYPE, processorTag, config, OPTIONAL_OUTPUT_MAP);

            if (CollectionUtils.isEmpty(inputMaps) && CollectionUtils.isEmpty(optionalInputMaps)) {
                throw new IllegalArgumentException(
                    "Please provide at least one non-empty input_map or optional_input_map for ML Inference Search Request Processor"
                );
            }

            if (CollectionUtils.isEmpty(outputMaps) && CollectionUtils.isEmpty(optionalOutputMaps)) {
                throw new IllegalArgumentException(
                    "Please provide at least one non-empty output_map or optional_output_map for ML Inference Search Request Processor"
                );
            }

            int maxPredictionTask = ConfigurationUtils
                .readIntProperty(TYPE, processorTag, config, MAX_PREDICTION_TASKS, DEFAULT_MAX_PREDICTION_TASKS);
            boolean ignoreMissing = ConfigurationUtils.readBooleanProperty(TYPE, processorTag, config, IGNORE_MISSING, false);
            String functionName = ConfigurationUtils
                .readStringProperty(TYPE, processorTag, config, FUNCTION_NAME, FunctionName.REMOTE.name());
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

            // convert model config user input data structure to Map<String, String>
            Map<String, String> modelConfigMaps = null;
            if (modelConfigInput != null) {
                modelConfigMaps = StringUtils.getParameterMap(modelConfigInput);
            }
            // Combine processInputMap and optionalInputMaps
            List<Map<String, String>> combinedInputMaps = ModelExecutor.combineMaps(inputMaps, optionalInputMaps);
            // Combine processOutputMap and optionalOutputMaps
            List<Map<String, String>> combinedOutputMaps = ModelExecutor.combineMaps(outputMaps, optionalOutputMaps);

            // check if the number of prediction tasks exceeds max prediction tasks
            if (combinedInputMaps != null && combinedInputMaps.size() > maxPredictionTask) {
                throw new IllegalArgumentException(
                    "The number of prediction task setting in this process is "
                        + combinedInputMaps.size()
                        + ". It exceeds the max_prediction_tasks of "
                        + maxPredictionTask
                        + ". Please reduce the size of input_map or optional_input_map or increase max_prediction_tasks."
                );
            }
            if (combinedOutputMaps != null && combinedInputMaps != null && combinedOutputMaps.size() != combinedInputMaps.size()) {
                throw new IllegalArgumentException(
                    "when output_maps/optional_output_maps and input_maps/optional_input_maps are provided, their length needs to match. The input is in length of "
                        + combinedInputMaps.size()
                        + ", while output_maps is in the length of "
                        + combinedOutputMaps.size()
                        + ". Please adjust mappings."
                );
            }

            return new MLInferenceSearchRequestProcessor(
                modelId,
                queryTemplate,
                inputMaps,
                outputMaps,
                optionalInputMaps,
                optionalOutputMaps,
                modelConfigMaps,
                maxPredictionTask,
                processorTag,
                description,
                ignoreMissing,
                functionName,
                fullResponsePath,
                ignoreFailure,
                modelInput,
                client,
                xContentRegistry
            );
        }
    }
}
