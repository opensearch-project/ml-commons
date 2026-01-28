/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.common.utils;

import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;
import static org.opensearch.ml.common.MLTask.LAST_UPDATE_TIME_FIELD;
import static org.opensearch.ml.common.MLTask.STATE_FIELD;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.UpdateDataObjectRequest;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.transport.client.Client;

import com.google.common.collect.ImmutableSet;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLTaskUtils {

    public static final ImmutableSet<MLTaskState> TASK_DONE_STATES = ImmutableSet
        .of(MLTaskState.COMPLETED, MLTaskState.COMPLETED_WITH_ERROR, MLTaskState.FAILED, MLTaskState.CANCELLED);

    /**
     * Updates an ML task document directly in the ML task index using SDKClient.
     * This method performs validation on the input parameters and updates the task with the provided fields.
     * It automatically adds a timestamp for the last update time.
     * For tasks that are being marked as done (completed, failed, etc.), it enables retry on conflict.
     *
     * @param taskId The ID of the ML task to update
     * @param tenantId The tenant ID for the task
     * @param updatedFields Map containing the fields to update in the ML task document
     * @param client The OpenSearch client to use for thread context
     * @param sdkClient The SDK client to use for the update operation
     * @param listener ActionListener to handle the response or failure of the update operation
     * @throws IllegalArgumentException if taskId is null/empty, updatedFields is null/empty, or if the state field contains an invalid MLTaskState
     */
    public static void updateMLTaskDirectly(
        String taskId,
        String tenantId,
        Map<String, Object> updatedFields,
        Client client,
        SdkClient sdkClient,
        ActionListener<UpdateResponse> listener
    ) {
        if (taskId == null || taskId.isEmpty()) {
            listener.onFailure(new IllegalArgumentException("Task ID is null or empty"));
            return;
        }

        if (updatedFields == null || updatedFields.isEmpty()) {
            listener.onFailure(new IllegalArgumentException("Updated fields is null or empty"));
            return;
        }

        if (updatedFields.containsKey(STATE_FIELD) && !(updatedFields.get(STATE_FIELD) instanceof MLTaskState)) {
            listener.onFailure(new IllegalArgumentException("Invalid task state"));
            return;
        }

        Map<String, Object> updatedContent = new HashMap<>(updatedFields);
        updatedContent.put(LAST_UPDATE_TIME_FIELD, Instant.now().toEpochMilli());

        UpdateDataObjectRequest.Builder requestBuilder = UpdateDataObjectRequest
            .builder()
            .index(ML_TASK_INDEX)
            .id(taskId)
            .tenantId(tenantId)
            .dataObject(updatedContent);

        if (updatedFields.containsKey(STATE_FIELD) && TASK_DONE_STATES.contains((MLTaskState) updatedFields.get(STATE_FIELD))) {
            requestBuilder.retryOnConflict(3);
        }

        UpdateDataObjectRequest updateDataObjectRequest = requestBuilder.build();

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<UpdateResponse> wrappedListener = ActionListener.runBefore(listener, context::restore);
            sdkClient.updateDataObjectAsync(updateDataObjectRequest).whenComplete((r, throwable) -> {
                if (throwable != null) {
                    Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                    log.error("Failed to update ML task {}", taskId, cause);
                    wrappedListener.onFailure(cause);
                } else {
                    try {
                        wrappedListener.onResponse(r.updateResponse());
                    } catch (Exception e) {
                        wrappedListener.onFailure(e);
                    }
                }
            });
        } catch (Exception e) {
            log.error("Failed to update ML task {}", taskId, e);
            listener.onFailure(e);
        }
    }
}
