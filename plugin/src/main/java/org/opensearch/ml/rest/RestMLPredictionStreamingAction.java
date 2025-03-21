/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.MLExceptionUtils.BATCH_INFERENCE_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.MLExceptionUtils.LOCAL_MODEL_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.MLExceptionUtils.REMOTE_INFERENCE_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_ALGORITHM;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MODEL_ID;
import static org.opensearch.ml.utils.RestActionUtils.getActionTypeFromRestRequest;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.google.common.base.Preconditions;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.arrow.spi.StreamManager;
import org.opensearch.arrow.spi.StreamReader;
import org.opensearch.arrow.spi.StreamTicket;
import org.opensearch.common.xcontent.support.XContentHttpChunk;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.plugin.MachineLearningPlugin;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.bytes.CompositeBytesReference;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.MediaType;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.ConnectorAction.ActionType;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.StreamingRestChannel;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Log4j2
public class RestMLPredictionStreamingAction extends BaseRestHandler {
    private static final String ML_PREDICTION_ACTION = "ml_prediction_streaming_action";

    private MLModelManager modelManager;

    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    private MachineLearningPlugin.StreamManagerWrapper streamManagerWrapper;

    /**
     * Constructor
     */
    public RestMLPredictionStreamingAction(MLModelManager modelManager, MLFeatureEnabledSetting mlFeatureEnabledSetting, MachineLearningPlugin.StreamManagerWrapper streamManagerWrapper) {
        this.modelManager = modelManager;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.streamManagerWrapper = streamManagerWrapper;
    }

    @Override
    public String getName() {
        return ML_PREDICTION_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(
                new Route(
                    RestRequest.Method.POST,
                    String.format(Locale.ROOT, "%s/models/{%s}/_predict/stream", ML_BASE_URI, PARAMETER_MODEL_ID)
                ),
                new Route(
                    RestRequest.Method.POST,
                    String.format(Locale.ROOT, "%s/models/{%s}/_batch_predict/stream", ML_BASE_URI, PARAMETER_MODEL_ID)
                )
            );
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        String algorithm = request.param(PARAMETER_ALGORITHM);
        String modelId = getParameterId(request, PARAMETER_MODEL_ID);
        Optional<FunctionName> functionName = modelManager.getOptionalModelFunctionName(modelId);

        if (algorithm == null && functionName.isPresent()) {
            algorithm = functionName.get().name();
        }

        final StreamingRestChannelConsumer consumer = (channel) -> {
            final MediaType mediaType = request.getMediaType();
            channel.prepareResponse(RestStatus.OK, Map.of("Content-Type", List.of(mediaType.mediaTypeWithoutParameters())));
            Flux
                .from(channel)
                .map(httpChunk -> httpChunk.content())
                .reduce(((bytesReference1, bytesReference2) -> CompositeBytesReference.of(bytesReference1, bytesReference2)))
                .doOnSuccess(bytesReference -> {
                    log.info("[jngz]:[http content]:{}", bytesReference.utf8ToString());
                    try {
                        MLPredictionTaskRequest taskRequest = getRequest(modelId, FunctionName.REMOTE.name(), request, bytesReference);
                        log.info("[jngz]: generated predict task request.");
                        client
                            .execute(
                                    MLPredictionTaskAction.INSTANCE,
                                    taskRequest,
                                    new ActionListener<MLTaskResponse>() {
                                        @Override
                                        public void onResponse(MLTaskResponse mlTaskResponse) {
                                            log.info("[jngz rest] get the response with ticket. The response is, {}", mlTaskResponse);
                                            ModelTensorOutput modelTensorOutput = (ModelTensorOutput) mlTaskResponse.getOutput();
                                            StreamTicket ticket = (StreamTicket) modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap().get("stream_ticket");
                                            log.info("[jngz rest] stream ticket is: {}", ticket);
                                            StreamManager streamManager = streamManagerWrapper.getStreamManager().get();
                                            StreamReader<VectorSchemaRoot> reader1 = streamManager.getStreamReader(ticket);
                                            try (StreamReader<VectorSchemaRoot> reader = streamManagerWrapper.getStreamManager().get().getStreamReader(ticket)) {
                                                int totalBatches = 0;
                                                VectorSchemaRoot vectorSchemaRoot = reader.getRoot();
                                                VarCharVector eventVector1 = (VarCharVector) vectorSchemaRoot.getVector("event");
                                                Preconditions.checkNotNull(reader.getRoot().getVector("event"));
                                                while (reader.next()) {
                                                    VarCharVector eventVector = (VarCharVector) reader.getRoot().getVector("event");
                                                    Preconditions.checkArgument(1 == eventVector.getValueCount());
                                                    String chunk = eventVector.get(0).toString();
                                                    log.info("[Chunk {}]: {}", totalBatches, chunk);
                                                    XContentBuilder builder = channel.newBuilder(mediaType, true);
                                                    builder.startObject();
                                                    builder.field("chunk", chunk);
                                                    builder.endObject();
                                                    channel.sendChunk(XContentHttpChunk.from(builder));
                                                    totalBatches++;
                                                }
                                                log.info("The number of batches: {}", totalBatches);
                                                channel.sendChunk(XContentHttpChunk.last());
                                            } catch (IOException e) {
                                                throw new MLException("Sending http chunks failed.");
                                            }
                                        }

                                        @Override
                                        public void onFailure(Exception e) {
                                            throw new MLException("Got an exception in MLPredictionTaskAction.", e);
                                        }
                                    }
                            );
                    } catch (IOException e) {
                        throw new MLException("Got an exception in flux.", e);
                    }
                }).onErrorComplete(ex -> {
                        if (ex instanceof Error) {
                            log.info("[jngz rest] got an error in flux");
                            return false;
                        }
                        try {
                            channel.sendResponse(new BytesRestResponse(channel, (Exception) ex));
                            return true;
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                .subscribe();
        };

        return channel -> {
            if (channel instanceof StreamingRestChannel) {
                consumer.accept((StreamingRestChannel) channel);
            } else {
                final ActionRequestValidationException validationError = new ActionRequestValidationException();
                validationError.addValidationError("Unable to initiate request / response streaming over non-streaming channel");
                channel.sendResponse(new BytesRestResponse(channel, validationError));
            }
        };
    }

    @Override
    public boolean supportsContentStream() {
        return true;
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public boolean allowsUnsafeBuffers() {
        return true;
    }

    /**
     * Creates a MLPredictionTaskRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLPredictionTaskRequest
     */
    @VisibleForTesting
    MLPredictionTaskRequest getRequest(String modelId, String algorithm, RestRequest request, BytesReference content) throws IOException {
        ActionType actionType = ActionType.from(getActionTypeFromRestRequest(request));
        if (FunctionName.REMOTE.name().equals(algorithm) && !mlFeatureEnabledSetting.isRemoteInferenceEnabled()) {
            throw new IllegalStateException(REMOTE_INFERENCE_DISABLED_ERR_MSG);
        } else if (FunctionName.isDLModel(FunctionName.from(algorithm.toUpperCase(Locale.ROOT)))
            && !mlFeatureEnabledSetting.isLocalModelEnabled()) {
            throw new IllegalStateException(LOCAL_MODEL_DISABLED_ERR_MSG);
        } else if (ActionType.BATCH_PREDICT == actionType && !mlFeatureEnabledSetting.isOfflineBatchInferenceEnabled()) {
            throw new IllegalStateException(BATCH_INFERENCE_DISABLED_ERR_MSG);
        } else if (!ActionType.isValidActionInModelPrediction(actionType)) {
            throw new IllegalArgumentException("Wrong action type in the rest request path!");
        }

        XContentParser parser = request
            .getMediaType()
            .xContent()
            .createParser(request.getXContentRegistry(), LoggingDeprecationHandler.INSTANCE, content.streamInput());

        // XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLInput mlInput = MLInput.parse(parser, algorithm, actionType);
        return new MLPredictionTaskRequest(modelId, mlInput, null, null);
    }

}
