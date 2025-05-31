package org.opensearch.ml.common.utils;

import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;
import static org.opensearch.ml.common.MLTask.LAST_UPDATE_TIME_FIELD;
import static org.opensearch.ml.common.MLTask.STATE_FIELD;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.action.support.WriteRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.transport.client.Client;

import com.google.common.collect.ImmutableSet;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLTaskUtils {

    public static final ImmutableSet<MLTaskState> TASK_DONE_STATES = ImmutableSet
        .of(MLTaskState.COMPLETED, MLTaskState.COMPLETED_WITH_ERROR, MLTaskState.FAILED, MLTaskState.CANCELLED);

    public static void updateMLTaskDirectly(
        String taskId,
        Map<String, Object> updatedFields,
        Client client,
        ActionListener<UpdateResponse> listener
    ) {
        try {
            if (updatedFields == null || updatedFields.isEmpty()) {
                listener.onFailure(new IllegalArgumentException("Updated fields is null or empty"));
                return;
            }
            UpdateRequest updateRequest = new UpdateRequest(ML_TASK_INDEX, taskId);
            Map<String, Object> updatedContent = new HashMap<>(updatedFields);
            updatedContent.put(LAST_UPDATE_TIME_FIELD, Instant.now().toEpochMilli());
            updateRequest.doc(updatedContent);
            updateRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
            if (updatedFields.containsKey(STATE_FIELD) && TASK_DONE_STATES.contains(updatedFields.containsKey(STATE_FIELD))) {
                updateRequest.retryOnConflict(3);
            }
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                client.update(updateRequest, ActionListener.runBefore(listener, context::restore));
            } catch (Exception e) {
                listener.onFailure(e);
            }
        } catch (Exception e) {
            log.error("Failed to update ML task {}", taskId, e);
            listener.onFailure(e);
        }
    }

}
