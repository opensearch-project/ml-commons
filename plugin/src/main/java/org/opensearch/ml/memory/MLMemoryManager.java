/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.memory;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.opensearch.client.Client;
import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.memory.action.conversation.CreateConversationAction;
import org.opensearch.ml.memory.action.conversation.CreateConversationRequest;
import org.opensearch.ml.memory.action.conversation.CreateConversationResponse;
import org.opensearch.ml.memory.action.conversation.CreateInteractionAction;
import org.opensearch.ml.memory.action.conversation.CreateInteractionRequest;
import org.opensearch.ml.memory.action.conversation.CreateInteractionResponse;
import org.opensearch.ml.memory.action.conversation.GetInteractionsAction;
import org.opensearch.ml.memory.action.conversation.GetInteractionsRequest;
import org.opensearch.ml.memory.action.conversation.GetInteractionsResponse;
import org.opensearch.ml.repackage.com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Memory manager for Memories. It contains ML memory related operations like create, read interactions etc.
 */
@Log4j2
@AllArgsConstructor
public class MLMemoryManager {
    public static final int DEFAULT_TIMEOUT_IN_MILLIS = 5000;

    private Client client;


    public String createConversation(String name, String applicationType) {

        CreateConversationResponse response = client
                .execute(CreateConversationAction.INSTANCE, new CreateConversationRequest(name, applicationType))
                .actionGet(DEFAULT_TIMEOUT_IN_MILLIS);
        log.info("createConversation: id: {}", response.getId());
        return response.getId();
    }

    public String createInteraction(
            String conversationId,
            String input,
            String promptTemplate,
            String response,
            String origin,
            Map<String, String> additionalInfo,
            String parentIntId,
            Integer traceNum
    ) {
        Preconditions.checkNotNull(conversationId);
        Preconditions.checkNotNull(input);
        Preconditions.checkNotNull(response);
        CreateInteractionResponse res = client
                .execute(
                        CreateInteractionAction.INSTANCE,
                        new CreateInteractionRequest(conversationId, input, promptTemplate, response, origin, additionalInfo, parentIntId, traceNum)
                )
                .actionGet(DEFAULT_TIMEOUT_IN_MILLIS);
        log.info("createInteraction: interactionId: {}", res.getId());
        return res.getId();
    }

    public List<Interaction> getInteractions(String conversationId, int lastNInteraction) {

        Preconditions.checkArgument(lastNInteraction > 0, "lastN must be at least 1.");

        log.info("Getting Interactions, conversationId {}, lastN {}", conversationId, lastNInteraction);

        List<Interaction> interactions = new ArrayList<>();
        int from = 0;
        boolean allInteractionsFetched = false;
        int maxResults = lastNInteraction;
        do {
            GetInteractionsResponse response = client
                    .execute(GetInteractionsAction.INSTANCE, new GetInteractionsRequest(conversationId, maxResults, from))
                    .actionGet(DEFAULT_TIMEOUT_IN_MILLIS);
            List<Interaction> list = response.getInteractions();
            if (list != null && !CollectionUtils.isEmpty(list)) {
                interactions.addAll(list);
                from += list.size();
                maxResults -= list.size();
                log.info("Interactions: {}, from: {}, maxResults: {}", interactions, from, maxResults);
            } else if (response.hasMorePages()) {
                // If we didn't get any results back, we ignore this flag and break out of the loop
                // to avoid an infinite loop.
                // But in the future, we may support this mode, e.g. DynamoDB.
                break;
            }
            log.info("Interactions: {}, from: {}, maxResults: {}", interactions, from, maxResults);
            allInteractionsFetched = !response.hasMorePages();
        } while (from < lastNInteraction && !allInteractionsFetched);

        return interactions;
    }

}
