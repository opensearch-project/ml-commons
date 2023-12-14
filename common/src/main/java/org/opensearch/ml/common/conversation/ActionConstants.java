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
package org.opensearch.ml.common.conversation;

/**
 * Constants for conversational actions
 */
public class ActionConstants {

    /** name of conversation Id field in all responses */
    public final static String CONVERSATION_ID_FIELD = "conversation_id";

    /** name of list of conversations in all responses */
    public final static String RESPONSE_CONVERSATION_LIST_FIELD = "conversations";
    /** name of list on interactions in all responses */
    public final static String RESPONSE_INTERACTION_LIST_FIELD = "interactions";
    /** name of list on interactions in all responses */
    public final static String RESPONSE_TRACES_LIST_FIELD = "traces";
    /** name of interaction Id field in all responses */
    public final static String RESPONSE_INTERACTION_ID_FIELD = "interaction_id";

    /** name of conversation name in all requests */
    public final static String REQUEST_CONVERSATION_NAME_FIELD = "name";
    /** name of maxResults field name in all requests */
    public final static String REQUEST_MAX_RESULTS_FIELD = "max_results";
    /** name of nextToken field name in all messages */
    public final static String NEXT_TOKEN_FIELD = "next_token";
    /** name of input field in all requests */
    public final static String INPUT_FIELD = "input";
    /** name of AI response field in all respopnses */
    public final static String AI_RESPONSE_FIELD = "response";
    /** name of origin field in all requests */
    public final static String RESPONSE_ORIGIN_FIELD = "origin";
    /** name of prompt template field in all requests */
    public final static String PROMPT_TEMPLATE_FIELD = "prompt_template";
    /** name of metadata field in all requests */
    public final static String ADDITIONAL_INFO_FIELD = "additional_info";
    /** name of metadata field in all requests */
    public final static String PARENT_INTERACTION_ID_FIELD = "parent_interaction_id";
    /** name of metadata field in all requests */
    public final static String TRACE_NUMBER_FIELD = "trace_number";
    /** name of success field in all requests */
    public final static String SUCCESS_FIELD = "success";

    private final static String BASE_REST_PATH = "/_plugins/_ml/memory/conversation";
    private final static String BASE_REST_INTERACTION_PATH = "/_plugins/_ml/memory/interaction";
    /** path for create conversation */
    public final static String CREATE_CONVERSATION_REST_PATH = BASE_REST_PATH + "/_create";
    /** path for get conversations */
    public final static String GET_CONVERSATIONS_REST_PATH  = BASE_REST_PATH + "/_list";
    /** path for update conversations */
    public final static String UPDATE_CONVERSATIONS_REST_PATH  = BASE_REST_PATH + "/{conversation_id}/_update";
    /** path for put interaction */
    /** path for create interaction */
    public final static String CREATE_INTERACTION_REST_PATH = BASE_REST_PATH + "/{conversation_id}/_create";
    /** path for get interactions */
    public final static String GET_INTERACTIONS_REST_PATH = BASE_REST_PATH + "/{conversation_id}/_list";
    /** path for get interactions */
    public final static String GET_TRACES_REST_PATH = "/_plugins/_ml/memory/trace" + "/{interaction_id}/_list";
    /** path for delete conversation */
    public final static String DELETE_CONVERSATION_REST_PATH = BASE_REST_PATH + "/{conversation_id}/_delete";
    /** path for search conversations */
    public final static String SEARCH_CONVERSATIONS_REST_PATH = BASE_REST_PATH + "/_search";
    /** path for search interactions */
    public final static String SEARCH_INTERACTIONS_REST_PATH = BASE_REST_PATH + "/{conversation_id}/_search";
    /** path for update interactions */
    public final static String UPDATE_INTERACTIONS_REST_PATH = BASE_REST_INTERACTION_PATH + "/{interaction_id}/_update";
    /** path for get conversation */
    public final static String GET_CONVERSATION_REST_PATH = BASE_REST_PATH + "/{conversation_id}";
    /** path for get interaction */
    public final static String GET_INTERACTION_REST_PATH = BASE_REST_PATH + "/{conversation_id}/{interaction_id}";

    /** default max results returned by get operations */
    public final static int DEFAULT_MAX_RESULTS = 10;

    /** default username for reporting security errors if no or malformed username */
    public final static String DEFAULT_USERNAME_FOR_ERRORS = "BAD_USER";
}