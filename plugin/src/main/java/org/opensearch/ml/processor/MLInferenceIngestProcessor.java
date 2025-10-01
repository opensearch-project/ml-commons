/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.processor;

import static org.opensearch.ml.processor.InferenceProcessorAttributes.*;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.GroupedActionListener;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ingest.AbstractProcessor;
import org.opensearch.ingest.ConfigurationUtils;
import org.opensearch.ingest.IngestDocument;
import org.opensearch.ingest.Processor;
import org.opensearch.ingest.ValueSource;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.script.ScriptService;
import org.opensearch.script.TemplateScript;
import org.opensearch.transport.client.Client;

import com.jayway.jsonpath.JsonPath;

/**
 * MLInferenceIngestProcessor requires a modelId string to call model inferences
 * maps fields in document for model input, and maps model inference output to new document fields
 * this processor also handles dot path notation for nested object( map of array) by rewriting json path accordingly
 */
public class MLInferenceIngestProcessor extends AbstractProcessor implements ModelExecutor {

    private static final Logger logger = LogManager.getLogger(MLInferenceIngestProcessor.class);

    public static final String DOT_SYMBOL = ".";
    private final InferenceProcessorAttributes inferenceProcessorAttributes;
    private final boolean ignoreMissing;
    private final String functionName;
    private final boolean fullResponsePath;
    private final boolean ignoreFailure;
    private final boolean override;
    private final String modelInput;
    private final ScriptService scriptService;
    private static Client client;
    public static final String TYPE = "ml_inference";
    public static final String DEFAULT_OUTPUT_FIELD_NAME = "inference_results";
    // allow to ignore a field from mapping is not present in the document, and when the outfield is not found in the
    // prediction outcomes, return the whole prediction outcome by skipping filtering
    public static final String IGNORE_MISSING = "ignore_missing";
    public static final String OVERRIDE = "override";
    public static final String FUNCTION_NAME = "function_name";
    public static final String FULL_RESPONSE_PATH = "full_response_path";
    public static final String MODEL_INPUT = "model_input";
    // At default, ml inference processor allows maximum 10 prediction tasks running in parallel
    // it can be overwritten using max_prediction_tasks when creating processor
    public static final int DEFAULT_MAX_PREDICTION_TASKS = 10;
    public static final String DEFAULT_MODEl_INPUT = "{ \"parameters\": ${ml_inference.parameters} }";
    private final NamedXContentRegistry xContentRegistry;

    protected MLInferenceIngestProcessor(
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
        ScriptService scriptService,
        Client client,
        NamedXContentRegistry xContentRegistry
    ) {
        super(tag, description);
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
        this.scriptService = scriptService;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
    }

    /**
     * This method is used to execute inference asynchronously,
     * supporting multiple predictions.
     *
     * @param ingestDocument The document to be processed.
     * @param handler        A consumer for handling the processing result or any exception occurred during processing.
     */
    @Override
    public void execute(IngestDocument ingestDocument, BiConsumer<IngestDocument, Exception> handler) {

        List<Map<String, String>> processInputMap = inferenceProcessorAttributes.getInputMaps();
        List<Map<String, String>> processOutputMap = inferenceProcessorAttributes.getOutputMaps();
        int inputMapSize = (processInputMap != null) ? processInputMap.size() : 0;

        GroupedActionListener<Void> batchPredictionListener = new GroupedActionListener<>(new ActionListener<Collection<Void>>() {
            @Override
            public void onResponse(Collection<Void> voids) {
                handler.accept(ingestDocument, null);
            }

            @Override
            public void onFailure(Exception e) {
                if (ignoreFailure) {
                    handler.accept(ingestDocument, null);
                } else {
                    handler.accept(null, e);
                }
            }
        }, Math.max(inputMapSize, 1));

        for (int inputMapIndex = 0; inputMapIndex < Math.max(inputMapSize, 1); inputMapIndex++) {
            try {
                processPredictions(ingestDocument, batchPredictionListener, processInputMap, processOutputMap, inputMapIndex, inputMapSize);
            } catch (Exception e) {
                batchPredictionListener.onFailure(e);
            }
        }
    }

    /**
     * This method was called previously within
     * execute( IngestDocument ingestDocument, BiConsumer (IngestDocument, Exception)  handler)
     * in the ml_inference ingest processor, it's not called.
     *
     * @param ingestDocument
     * @throws Exception
     */
    @Override
    public IngestDocument execute(IngestDocument ingestDocument) throws Exception {
        throw new UnsupportedOperationException("this method should not get executed.");
    }

    /**
     * process predictions for one model for multiple rounds of predictions
     * ingest documents after prediction rounds are completed,
     * when no input mappings provided, default to add all fields to model input fields,
     * when no output mapping provided, default to output as
     * "inference_results" field (the same format as predict API)
     *
     * @param ingestDocument          The IngestDocument object containing the data to be processed.
     * @param batchPredictionListener The GroupedActionListener for batch prediction.
     * @param processInputMap         A list of maps containing input field mappings.
     * @param processOutputMap        A list of maps containing output field mappings.
     * @param inputMapIndex           The current index of the inputMap.
     * @param inputMapSize            The size of inputMap.
     */
    private void processPredictions(
        IngestDocument ingestDocument,
        GroupedActionListener<Void> batchPredictionListener,
        List<Map<String, String>> processInputMap,
        List<Map<String, String>> processOutputMap,
        int inputMapIndex,
        int inputMapSize
    ) throws IOException {
        Map<String, String> modelParameters = new HashMap<>();
        Map<String, String> modelConfigs = new HashMap<>();

        if (inferenceProcessorAttributes.getModelConfigMaps() != null) {
            modelParameters.putAll(inferenceProcessorAttributes.getModelConfigMaps());
            modelConfigs.putAll(inferenceProcessorAttributes.getModelConfigMaps());
        }

        Map<String, Object> ingestDocumentSourceAndMetaData = new HashMap<>();
        ingestDocumentSourceAndMetaData.putAll(ingestDocument.getSourceAndMetadata());
        ingestDocumentSourceAndMetaData.put(IngestDocument.INGEST_KEY, ingestDocument.getIngestMetadata());

        Map<String, List<String>> newOutputMapping = new HashMap<>();
        if (processOutputMap != null) {

            Map<String, String> outputMapping = processOutputMap.get(inputMapIndex);
            for (Map.Entry<String, String> entry : outputMapping.entrySet()) {
                String newDocumentFieldName = entry.getKey();
                List<String> dotPathsInArray = writeNewDotPathForNestedObject(ingestDocumentSourceAndMetaData, newDocumentFieldName);
                newOutputMapping.put(newDocumentFieldName, dotPathsInArray);
            }

            for (Map.Entry<String, String> entry : outputMapping.entrySet()) {
                String newDocumentFieldName = entry.getKey();
                List<String> dotPaths = newOutputMapping.get(newDocumentFieldName);

                int existingFields = 0;
                for (String path : dotPaths) {
                    if (ingestDocument.hasField(path)) {
                        existingFields++;
                    }
                }
                if (!override && existingFields == dotPaths.size()) {
                    logger.debug("{} already exists in the ingest document. Removing it from output mapping", newDocumentFieldName);
                    newOutputMapping.remove(newDocumentFieldName);
                }
            }
            if (newOutputMapping.size() == 0) {
                batchPredictionListener.onResponse(null);
                return;
            }
        }
        // when no input mapping is provided, default to read all fields from documents as model input
        if (inputMapSize == 0) {
            Set<String> documentFields = ingestDocument.getSourceAndMetadata().keySet();
            for (String field : documentFields) {
                getMappedModelInputFromDocuments(ingestDocument, modelParameters, field, field);
            }

        } else {
            Map<String, String> inputMapping = processInputMap.get(inputMapIndex);
            for (Map.Entry<String, String> entry : inputMapping.entrySet()) {
                // model field as key, document field as value
                String modelInputFieldName = entry.getKey();
                String documentFieldName = entry.getValue();
                getMappedModelInputFromDocuments(ingestDocument, modelParameters, documentFieldName, modelInputFieldName);
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
                if (processOutputMap == null || processOutputMap.isEmpty()) {
                    appendFieldValue(mlOutput, null, DEFAULT_OUTPUT_FIELD_NAME, ingestDocument);
                } else {
                    // outMapping serves as a filter to modelTensorOutput, the fields that are not specified
                    // in the outputMapping will not write to document
                    Map<String, String> outputMapping = processOutputMap.get(inputMapIndex);

                    for (Map.Entry<String, String> entry : outputMapping.entrySet()) {
                        // document field as key, model field as value
                        String newDocumentFieldName = entry.getKey();
                        String modelOutputFieldName = entry.getValue();
                        if (!newOutputMapping.containsKey(newDocumentFieldName)) {
                            continue;
                        }
                        appendFieldValue(mlOutput, modelOutputFieldName, newDocumentFieldName, ingestDocument);
                    }
                }
                batchPredictionListener.onResponse(null);
            }

            @Override
            public void onFailure(Exception e) {
                batchPredictionListener.onFailure(e);
            }
        });

    }

    /**
     * Retrieves the mapped model input from the IngestDocument and updates the model parameters.
     *
     * @param ingestDocument      The IngestDocument object containing the data.
     * @param modelParameters     The map to store the model parameters.
     * @param documentFieldName   The name of the field in the IngestDocument.
     * @param modelInputFieldName The name of the model input field.
     */
    private void getMappedModelInputFromDocuments(
        IngestDocument ingestDocument,
        Map<String, String> modelParameters,
        String documentFieldName,
        String modelInputFieldName
    ) {
        // if users used standard dot path, try getFieldPath from document
        String originalFieldPath = getFieldPath(ingestDocument, documentFieldName);
        if (originalFieldPath != null) {
            Object documentFieldValue = ingestDocument.getFieldValue(originalFieldPath, Object.class);
            String documentFieldValueAsString = toString(documentFieldValue);
            updateModelParameters(modelInputFieldName, documentFieldValueAsString, modelParameters);
            return;
        }
        // If the standard dot path fails, try to check for a nested array using JSON path
        if (StringUtils.isValidJSONPath(documentFieldName)) {
            Map<String, Object> sourceObject = ingestDocument.getSourceAndMetadata();
            Object fieldValue = JsonPath.using(suppressExceptionConfiguration).parse(sourceObject).read(documentFieldName);

            if (fieldValue != null) {
                if (fieldValue instanceof List) {
                    List<?> fieldValueList = (List<?>) fieldValue;
                    if (!fieldValueList.isEmpty()) {
                        updateModelParameters(modelInputFieldName, toString(fieldValueList), modelParameters);
                    } else if (!ignoreMissing) {
                        throw new IllegalArgumentException("Cannot find field name defined from input map: " + documentFieldName);
                    }
                } else {
                    updateModelParameters(modelInputFieldName, toString(fieldValue), modelParameters);
                }
            } else if (!ignoreMissing) {
                throw new IllegalArgumentException("Cannot find field name defined from input map: " + documentFieldName);
            }
        } else {
            throw new IllegalArgumentException("Cannot find field name defined from input map: " + documentFieldName);
        }
    }

    /**
     * This method supports mapping multiple document fields to the same model input field.
     * It checks if the given model input field name already exists in the modelParameters map.
     * If it exists, the method appends the originalFieldValueAsString to the existing value,
     * which is expected to be a list. If the model input field name does not exist in the map,
     * it adds a new entry with the originalFieldValueAsString as the value.
     *
     * @param modelInputFieldName        the name of the model input field
     * @param originalFieldValueAsString the value of the document field to be mapped
     * @param modelParameters            a map containing the model input fields and their values
     */
    private void updateModelParameters(String modelInputFieldName, String originalFieldValueAsString, Map<String, String> modelParameters) {

        if (modelParameters.containsKey(modelInputFieldName)) {
            Object existingValue = modelParameters.get(modelInputFieldName);
            List<Object> updatedList = (List<Object>) existingValue;
            updatedList.add(originalFieldValueAsString);
            modelParameters.put(modelInputFieldName, toString(updatedList));
        } else {
            modelParameters.put(modelInputFieldName, originalFieldValueAsString);
        }

    }

    /**
     * Retrieves the field path from the given IngestDocument for the specified documentFieldName.
     *
     * @param ingestDocument    the IngestDocument containing the field
     * @param documentFieldName the name of the field to retrieve the path for
     * @return the field path if the field exists, null otherwise
     */
    private String getFieldPath(IngestDocument ingestDocument, String documentFieldName) {
        if (Strings.isNullOrEmpty(documentFieldName) || !ingestDocument.hasField(documentFieldName, true)) {
            return null;
        }
        return documentFieldName;
    }

    /**
     * Appends the model output value to the specified field in the IngestDocument without modifying the source.
     *
     * @param mlOutput    the MLOutput containing the model output
     * @param modelOutputFieldName the name of the field in the model output
     * @param newDocumentFieldName the name of the field in the IngestDocument to append the value to
     * @param ingestDocument       the IngestDocument to append the value to
     */
    private void appendFieldValue(
        MLOutput mlOutput,
        String modelOutputFieldName,
        String newDocumentFieldName,
        IngestDocument ingestDocument
    ) {

        if (mlOutput == null) {
            throw new RuntimeException("model inference output is null");
        }

        // Check if transformation is needed
        String baseFieldName = OutputTransformations.getBaseFieldName(modelOutputFieldName);
        Object modelOutputValue = getModelOutputValue(mlOutput, baseFieldName, ignoreMissing, fullResponsePath);

        // Apply transformation if specified
        if (OutputTransformations.hasTransformation(modelOutputFieldName)) {
            modelOutputValue = OutputTransformations.applyMeanPooling(modelOutputValue);
        }

        Map<String, Object> ingestDocumentSourceAndMetaData = new HashMap<>();
        ingestDocumentSourceAndMetaData.putAll(ingestDocument.getSourceAndMetadata());
        ingestDocumentSourceAndMetaData.put(IngestDocument.INGEST_KEY, ingestDocument.getIngestMetadata());
        List<String> dotPathsInArray = writeNewDotPathForNestedObject(ingestDocumentSourceAndMetaData, newDocumentFieldName);

        if (dotPathsInArray.size() == 1) {
            ValueSource ingestValue = ValueSource.wrap(modelOutputValue, scriptService);
            TemplateScript.Factory ingestField = ConfigurationUtils
                .compileTemplate(TYPE, tag, dotPathsInArray.get(0), dotPathsInArray.get(0), scriptService);
            ingestDocument.setFieldValue(ingestField, ingestValue, ignoreMissing);
        } else {
            if (!(modelOutputValue instanceof List)) {
                throw new IllegalArgumentException("Model output is not an array, cannot assign to array in documents.");
            }
            List<?> modelOutputValueArray = (List<?>) modelOutputValue;
            // check length of the prediction array to be the same of the document array
            if (dotPathsInArray.size() != modelOutputValueArray.size()) {
                throw new RuntimeException(
                    "the prediction field: "
                        + modelOutputFieldName
                        + " is an array in size of "
                        + modelOutputValueArray.size()
                        + " but the document field array from field "
                        + newDocumentFieldName
                        + " is in size of "
                        + dotPathsInArray.size()
                );
            }
            // Iterate over dotPathInArray
            for (int i = 0; i < dotPathsInArray.size(); i++) {
                String dotPathInArray = dotPathsInArray.get(i);
                Object modelOutputValueInArray = modelOutputValueArray.get(i);
                ValueSource ingestValue = ValueSource.wrap(modelOutputValueInArray, scriptService);
                TemplateScript.Factory ingestField = ConfigurationUtils
                    .compileTemplate(TYPE, tag, dotPathInArray, dotPathInArray, scriptService);
                ingestDocument.setFieldValue(ingestField, ingestValue, ignoreMissing);
            }
        }
    }

    @Override
    public String getType() {
        return TYPE;
    }

    public static class Factory implements Processor.Factory {

        private final ScriptService scriptService;
        private final Client client;
        private final NamedXContentRegistry xContentRegistry;

        /**
         * Constructs a new instance of the Factory class.
         *
         * @param scriptService the ScriptService instance to be used by the Factory
         * @param client        the Client instance to be used by the Factory
         */
        public Factory(ScriptService scriptService, Client client, NamedXContentRegistry xContentRegistry) {
            this.scriptService = scriptService;
            this.client = client;
            this.xContentRegistry = xContentRegistry;
        }

        /**
         * Creates a new instance of the MLInferenceIngestProcessor.
         *
         * @param registry     a map of registered processor factories
         * @param processorTag a unique tag for the processor
         * @param description  a description of the processor
         * @param config       a map of configuration properties for the processor
         * @return a new instance of the MLInferenceIngestProcessor
         * @throws Exception if there is an error creating the processor
         */
        @Override
        public MLInferenceIngestProcessor create(
            Map<String, Processor.Factory> registry,
            String processorTag,
            String description,
            Map<String, Object> config
        ) throws Exception {
            String modelId = ConfigurationUtils.readStringProperty(TYPE, processorTag, config, MODEL_ID);
            Map<String, Object> modelConfigInput = ConfigurationUtils.readOptionalMap(TYPE, processorTag, config, MODEL_CONFIG);
            List<Map<String, String>> inputMaps = ConfigurationUtils.readOptionalList(TYPE, processorTag, config, INPUT_MAP);
            List<Map<String, String>> outputMaps = ConfigurationUtils.readOptionalList(TYPE, processorTag, config, OUTPUT_MAP);
            int maxPredictionTask = ConfigurationUtils
                .readIntProperty(TYPE, processorTag, config, MAX_PREDICTION_TASKS, DEFAULT_MAX_PREDICTION_TASKS);
            boolean ignoreMissing = ConfigurationUtils.readBooleanProperty(TYPE, processorTag, config, IGNORE_MISSING, false);
            boolean override = ConfigurationUtils.readBooleanProperty(TYPE, processorTag, config, OVERRIDE, false);
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

            boolean ignoreFailure = ConfigurationUtils
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
            if (inputMaps != null && outputMaps != null && outputMaps.size() != inputMaps.size()) {
                throw new IllegalArgumentException("The length of output_map and the length of input_map do no match.");
            }

            return new MLInferenceIngestProcessor(
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
                scriptService,
                client,
                xContentRegistry
            );
        }
    }

}
