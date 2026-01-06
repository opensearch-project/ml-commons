/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.LatchedActionListener;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.remote.RemoteInferenceMLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.secure_sm.AccessController;
import org.opensearch.transport.client.Client;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

@Log4j2
@EqualsAndHashCode
@Getter
public class ModelGuardrail extends Guardrail {
    public static final String MODEL_ID_FIELD = "model_id";
    public static final String RESPONSE_FILTER_FIELD = "response_filter";
    public static final String RESPONSE_VALIDATION_REGEX_FIELD = "response_validation_regex";

    private String modelId;
    private String responseFilter;
    private String responseAccept;
    private NamedXContentRegistry xContentRegistry;
    private Client client;
    private SdkClient sdkClient;
    private String tenantId;
    private Pattern regexAcceptPattern;

    @Builder(toBuilder = true)
    public ModelGuardrail(String modelId, String responseFilter, String responseAccept) {
        this.modelId = modelId;
        this.responseFilter = responseFilter;
        this.responseAccept = responseAccept;
    }

    public ModelGuardrail(@NonNull Map<String, Object> params) {
        this(
            (String) params.get(MODEL_ID_FIELD),
            (String) params.get(RESPONSE_FILTER_FIELD),
            (String) params.get(RESPONSE_VALIDATION_REGEX_FIELD)
        );
    }

    public ModelGuardrail(StreamInput input) throws IOException {
        modelId = input.readString();
        responseFilter = input.readString();
        responseAccept = input.readString();
    }

    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(modelId);
        out.writeString(responseFilter);
        out.writeString(responseAccept);
    }

    private Boolean validateAcceptRegex(String input) {
        Matcher matcher = regexAcceptPattern.matcher(input);
        return matcher.matches();
    }

    @Override
    public Boolean validate(String in, Map<String, String> parameters) {
        String input = parameters == null ? null : parameters.get("question");
        if (input == null || input.isEmpty()) {
            log.info("Guardrail request is empty.");
            return true;
        }
        log.info("Guardrail request: {}", input);
        AtomicBoolean isAccepted = new AtomicBoolean(true);
        ActionListener<MLTaskResponse> internalListener = ActionListener.wrap(predictionResponse -> {
            ModelTensorOutput output = (ModelTensorOutput) predictionResponse.getOutput();
            ModelTensor tensor = output.getMlModelOutputs().get(0).getMlModelTensors().get(0);
            String guardrailResponse = AccessController.doPrivileged(() -> gson.toJson(tensor.getDataAsMap().get("response")));
            log.info("Guardrail response: {}", guardrailResponse);
            if (!validateAcceptRegex(guardrailResponse)) {
                isAccepted.set(false);
            }
        }, e -> { log.error("[ModelGuardrail] Failed to get prediction response.", e); });
        ActionListener<MLTaskResponse> actionListener = wrapActionListener(internalListener, res -> {
            MLTaskResponse predictionResponse = MLTaskResponse.fromActionResponse(res);
            return predictionResponse;
        });
        CountDownLatch latch = new CountDownLatch(1);
        Map<String, String> guardrailModelParams = new HashMap<>();
        guardrailModelParams.put("question", input);
        if (responseFilter != null && !responseFilter.isEmpty()) {
            guardrailModelParams.put("response_filter", responseFilter);
        }
        log.info("Guardrail resFilter: {}", responseFilter);
        ActionRequest request = new MLPredictionTaskRequest(
            modelId,
            RemoteInferenceMLInput
                .builder()
                .algorithm(FunctionName.REMOTE)
                .inputDataset(RemoteInferenceInputDataSet.builder().parameters(guardrailModelParams).build())
                .build()
        );
        client.execute(MLPredictionTaskAction.INSTANCE, request, new LatchedActionListener(actionListener, latch));
        try {
            latch.await(5, SECONDS);
        } catch (InterruptedException e) {
            log.error("[ModelGuardrail] Validation was timeout.", e);
        }

        return isAccepted.get();
    }

    @Override
    public void init(NamedXContentRegistry xContentRegistry, Client client, SdkClient sdkClient, String tenantId) {
        this.xContentRegistry = xContentRegistry;
        this.client = client;
        this.sdkClient = sdkClient;
        this.tenantId = tenantId;
        regexAcceptPattern = Pattern.compile(responseAccept);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (modelId != null) {
            builder.field(MODEL_ID_FIELD, modelId);
        }
        if (responseFilter != null) {
            builder.field(RESPONSE_FILTER_FIELD, responseFilter);
        }
        if (responseAccept != null) {
            builder.field(RESPONSE_VALIDATION_REGEX_FIELD, responseAccept);
        }
        builder.endObject();
        return builder;
    }

    public static ModelGuardrail parse(XContentParser parser) throws IOException {
        String modelId = null;
        String responseFilter = null;
        String responseAccept = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MODEL_ID_FIELD:
                    modelId = parser.text();
                    break;
                case RESPONSE_FILTER_FIELD:
                    responseFilter = parser.text();
                    break;
                case RESPONSE_VALIDATION_REGEX_FIELD:
                    responseAccept = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return ModelGuardrail.builder().modelId(modelId).responseFilter(responseFilter).responseAccept(responseAccept).build();
    }

    private <T extends ActionResponse> ActionListener<T> wrapActionListener(
        final ActionListener<T> listener,
        final Function<ActionResponse, T> recreate
    ) {
        ActionListener<T> actionListener = ActionListener.wrap(r -> {
            listener.onResponse(recreate.apply(r));
            ;
        }, e -> { listener.onFailure(e); });
        return actionListener;
    }
}
