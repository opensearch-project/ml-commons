package org.opensearch.ml.action.prompt;

import lombok.extern.log4j.Log4j2;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.MLPrompt;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptAction;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptInput;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptRequest;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptResponse;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.PutDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import java.io.IOException;
import java.time.Instant;

import static org.opensearch.ml.common.CommonValue.ML_PROMPT_INDEX;

@Log4j2
public class TransportCreatePromptAction extends HandledTransportAction<ActionRequest, MLCreatePromptResponse> {
    private final MLIndicesHandler mlIndicesHandler;
    private final Client client;
    private final SdkClient sdkClient;

    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public TransportCreatePromptAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLIndicesHandler mlIndicesHandler,
        Client client,
        SdkClient sdkClient,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLCreatePromptAction.NAME, transportService, actionFilters, MLCreatePromptRequest::new);
        this.mlIndicesHandler = mlIndicesHandler;
        this.client = client;
        this.sdkClient = sdkClient;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLCreatePromptResponse> listener) {
        MLCreatePromptRequest mlCreatePromptRequest = MLCreatePromptRequest.fromActionRequest(request);
        MLCreatePromptInput mlCreatePromptInput = mlCreatePromptRequest.getMlCreatePromptInput();

        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, mlCreatePromptInput.getTenantId(), listener)) {
            return;
        }
        if (mlCreatePromptInput.isDryRun()) {
            MLCreatePromptResponse response = new MLCreatePromptResponse(MLCreatePromptInput.DRY_RUN_PROMPT_NAME);
            listener.onResponse(response);
            return;
        }

        try {
            MLPrompt mlPrompt = MLPrompt
                    .builder()
                    .name(mlCreatePromptInput.getName())
                    .description(mlCreatePromptInput.getDescription())
                    .prompt(mlCreatePromptInput.getPrompt())
                    .tenantId(mlCreatePromptInput.getTenantId())
                    .tag(mlCreatePromptInput.getTag())
                    .build();

            indexPrompt(mlPrompt, listener);
        } catch (Exception e) {
            log.error("Failed to create a Prompt", e);
            listener.onFailure(e);
        }
    }

    private void indexPrompt(MLPrompt prompt, ActionListener<MLCreatePromptResponse> listener) {
        log.info("prompt created, indexing into the prompt system index");
        mlIndicesHandler.initMLPromptIndex(ActionListener.wrap(indexCreated -> {
            if (!indexCreated) {
                listener.onFailure(new RuntimeException("No response to create ML Prompt Index"));
                return;
            }
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                prompt.setCreateTime(Instant.now());
                prompt.setLastUpdateTime(Instant.now());
                sdkClient
                        .putDataObjectAsync(
                                PutDataObjectRequest
                                        .builder()
                                        .tenantId(prompt.getTenantId())
                                        .index(ML_PROMPT_INDEX)
                                        .dataObject(prompt)
                                        .build()
                        ).whenComplete((r, throwable) -> {
                            context.restore();
                            if (throwable != null) {
                                Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                                log.error("Failed to create ML prompt", cause);
                                listener.onFailure(cause);
                            } else {
                                try {
                                    IndexResponse indexResponse = IndexResponse.fromXContent(r.parser());
                                    log.info(
                                            "Prompt creation result: {}, prompt id: {}",
                                            indexResponse.getResult(),
                                            indexResponse.getId()
                                    );
                                    listener.onResponse(new MLCreatePromptResponse(indexResponse.getId()));
                                } catch (IOException e) {
                                    listener.onFailure(e);
                                }
                            }
                        });
            } catch (Exception e) {
                log.error("Failed to save ML prompt", e);
                listener.onFailure(e);
            }
        }, e -> {
            log.error("Failed to init ML prompt index", e);
            listener.onFailure(e);
        }));
    }
}
