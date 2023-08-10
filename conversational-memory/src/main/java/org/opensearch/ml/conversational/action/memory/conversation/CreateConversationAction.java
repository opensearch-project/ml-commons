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
package org.opensearch.ml.conversational.action.memory.conversation;

import org.opensearch.action.ActionType;

/**
 * Action for creating a new conversation in the index
 */
public class CreateConversationAction extends ActionType<CreateConversationResponse> {
    /** Instance of this */
    public static final CreateConversationAction INSTANCE = new CreateConversationAction();
    /** Name of this action */
    public static final String NAME = "cluster:admin/opensearch/ml/conversational/conversation/create";

    private CreateConversationAction() { super(NAME, CreateConversationResponse::new); }
}