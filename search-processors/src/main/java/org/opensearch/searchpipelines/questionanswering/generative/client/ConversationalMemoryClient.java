/*
 * Copyright 2023 Aryn
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensearch.searchpipelines.questionanswering.generative.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * An OpenSearch client wrapper for conversational memory related calls.
 */
@Log4j2
@AllArgsConstructor
public class ConversationalMemoryClient {

    private final static Logger logger = LogManager.getLogger();
    private final static long DEFAULT_TIMEOUT_IN_MILLIS = 10_000l;

    private Client client;

    public String createConversation(String name) {

        CreateConversationResponse response = client
            .execute(CreateConversationAction.INSTANCE, new CreateConversationRequest(name))
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
        Map<String, String> additionalInfo
    ) {
        Objects.requireNonNull(conversationId);
        Objects.requireNonNull(input);
        Objects.requireNonNull(response);
        CreateInteractionResponse res = client
            .execute(
                CreateInteractionAction.INSTANCE,
                new CreateInteractionRequest(conversationId, input, promptTemplate, response, origin, additionalInfo)
            )
            .actionGet(DEFAULT_TIMEOUT_IN_MILLIS);
        log.info("createInteraction: interactionId: {}", res.getId());
        return res.getId();
    }

    public List<Interaction> getInteractions(String conversationId, int lastN) {

        Validate.isTrue(lastN > 0, "lastN must be at least 1.");

        log.info("In getInteractions, conversationId {}, lastN {}", conversationId, lastN);

        List<Interaction> interactions = new ArrayList<>();
        int from = 0;
        boolean allInteractionsFetched = false;
        int maxResults = lastN;
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
        } while (from < lastN && !allInteractionsFetched);

        return interactions;
    }
}
