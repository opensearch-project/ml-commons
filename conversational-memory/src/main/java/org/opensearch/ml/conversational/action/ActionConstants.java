/*
 * Copyright Aryn, Inc 2023
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
package org.opensearch.ml.conversational.action;

/**
 * Constants for conversational actions
 */
public class ActionConstants {

    /** name of conversation Id field in all responses */
    public final static String CONVERSATION_ID_FIELD = "conversationId";

    /** name of list of conversations in all responses */
    public final static String RESPONSE_CONVERSATION_LIST_FIELD = "conversations";
    /** name of list on interactions in all responses */
    public final static String RESPONSE_INTER_LIST_FIELD = "interactions";
    /** name of interaction Id field in all responses */
    public final static String RESPONSE_INTER_ID_FIELD = "interactionId";

    /** name of conversation name in all requests */
    public final static String REQUEST_CONVERSATION_NAME_FIELD = "name";
    /** name of maxResults field name in all requests */
    public final static String REQUEST_MAX_RESULTS_FIELD = "maxResults";
    /** name of nextToken field name in all messages */
    public final static String NEXT_TOKEN_FIELD = "nextToken";
    /** name of input field in all requests */
    public final static String INPUT_FIELD = "input";
    /** name of prompt field in all requests */
    public final static String PROMPT_FIELD = "prompt";
    /** name of AI response field in all respopnses */
    public final static String AI_RESPONSE_FIELD = "response";
    /** name of agent field in all requests */
    public final static String AI_AGENT_FIELD = "agent";
    /** name of interaction attributes field in all requests */
    public final static String INTER_ATTRIBUTES_FIELD = "attributes";
    /** name of success field in all requests */
    public final static String SUCCESS_FIELD = "success";

    /** path for create conversation */
    public final static String CREATE_CONVERSATION_PATH = "/_plugins/ml/conversational/memory";
    /** path for list conversations */
    public final static String LIST_CONVERSATIONS_PATH  = "/_plugins/ml/conversational/memory";
    /** path for put interaction */
    public final static String CREATE_INTERACTION_PATH = "/_plugins/ml/conversational/memory/{conversationId}";
    /** path for get interactions */
    public final static String GET_INTERACTIONS_PATH = "/_plugins/ml/conversational/memory/{conversationId}";
    /** path for delete conversation */
    public final static String DELETE_CONVERSATION_PATH = "/_plugins/ml/conversational/memory/{conversationId}";

    /** default max results returned by get operations */
    public final static int DEFAULT_MAX_RESULTS = 10;
}