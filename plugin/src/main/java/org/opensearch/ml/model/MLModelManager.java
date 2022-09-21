/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.model;

import com.google.common.collect.ImmutableMap;
import lombok.extern.log4j.Log4j2;
import org.opensearch.action.ActionListener;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.rest.RestStatus;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;

@Log4j2
public class MLModelManager {

    private final Client client;
    NamedXContentRegistry xContentRegistry;

    public MLModelManager(Client client, NamedXContentRegistry xContentRegistry) {
        this.client = client;
        this.xContentRegistry = xContentRegistry;
    }

    public void getModel(String modelId, ActionListener<MLModel> listener) {
        GetRequest getRequest = new GetRequest();
        getRequest.index(ML_MODEL_INDEX).id(modelId);
        client.get(getRequest, ActionListener.wrap(r-> {
            if (r != null && r.isExists()) {
                try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, r.getSourceAsBytesRef())) {
                    ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                    MLModel mlModel = MLModel.parse(parser);
                    mlModel.setModelId(modelId);
                    listener.onResponse(mlModel);
                } catch (Exception e){
                    log.error("Failed to parse ml task" + r.getId(), e);
                    listener.onFailure(e);
                }
            } else {
                listener.onFailure(new MLResourceNotFoundException("Fail to find model"));
            }
        }, e-> {
            listener.onFailure(e);
        }));
    }

    public void updateModel(String modelId, ImmutableMap<String, Object> updatedFields) {
        updateModel(modelId, updatedFields, ActionListener.wrap(response -> {
            if (response.status() == RestStatus.OK) {
                log.debug("Updated ML model successfully: {}, model id: {}", response.status(), modelId);
            } else {
                log.error("Failed to update ML model {}, status: {}", modelId, response.status());
            }
        }, e -> { log.error("Failed to update ML model: " + modelId, e); }));
    }

    public void updateModel(String modelId, ImmutableMap<String, Object> updatedFields,
                            ActionListener<UpdateResponse> listener) {
        try {
            if (updatedFields == null || updatedFields.size() == 0) {
                listener.onFailure(new IllegalArgumentException("Updated fields is null or empty"));
                return;
            }
            UpdateRequest updateRequest = new UpdateRequest(ML_MODEL_INDEX, modelId);
            updateRequest.doc(updatedFields);
            updateRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                client.update(updateRequest, ActionListener.runBefore(listener, () -> context.restore()));
            } catch (Exception e) {
                listener.onFailure(e);
            }
        } catch (Exception e) {
            log.error("Failed to update ML model " + modelId, e);
            listener.onFailure(e);
        }
    }

    public String getModelChunkId(String modelId, Integer chunkNumber) {
        return modelId + "_" + chunkNumber;
    }
}
