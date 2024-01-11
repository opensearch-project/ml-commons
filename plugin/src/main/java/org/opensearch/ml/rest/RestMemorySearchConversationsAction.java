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
package org.opensearch.ml.rest;

import java.util.List;

import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.ml.common.conversation.ConversationMeta;
import org.opensearch.ml.common.conversation.ConversationalIndexConstants;
import org.opensearch.ml.memory.action.conversation.SearchConversationsAction;

public class RestMemorySearchConversationsAction extends AbstractMLSearchAction<ConversationMeta> {
    private static final String SEARCH_CONVERSATIONS_NAME = "conversation_memory_search_conversations";

    public RestMemorySearchConversationsAction() {
        super(
            List.of(ActionConstants.SEARCH_CONVERSATIONS_REST_PATH),
            ConversationalIndexConstants.META_INDEX_NAME,
            ConversationMeta.class,
            SearchConversationsAction.INSTANCE
        );
    }

    @Override
    public String getName() {
        return SEARCH_CONVERSATIONS_NAME;
    }
}
