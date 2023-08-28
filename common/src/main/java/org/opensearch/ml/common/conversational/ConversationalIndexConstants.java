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
package org.opensearch.ml.common.conversational;

/**
 * Class containing a bunch of constant defining how the conversational indices are formatted
 */
public class ConversationalIndexConstants {
    /** Version of the meta index schema */
    public final static Integer META_INDEX_SCHEMA_VERSION = 1;
    /** Name of the conversational metadata index */
    public final static String META_INDEX_NAME = ".conversational-meta";
    /** Name of the metadata field for initial timestamp */
    public final static String META_CREATED_FIELD = "create_time";
    /** Name of the metadata field for name of the conversation */
    public final static String META_NAME_FIELD = "name";
    /** Name of the owning user field in all indices */
    public final static String USER_FIELD = "user";
    /** Mappings for the conversational metadata index */
    public final static String META_MAPPING = "{\n"
        + "    \"_meta\": {\n"
        + "        \"schema_version\": " + META_INDEX_SCHEMA_VERSION + "\n" 
        + "    },\n"
        + "    \"properties\": {\n"
        + "        \""
        + META_NAME_FIELD
        + "\": {\"type\": \"keyword\"},\n"
        + "        \""
        + META_CREATED_FIELD
        + "\": {\"type\": \"date\", \"format\": \"strict_date_optional_time||epoch_millis\"},\n"
        + "        \""
        + USER_FIELD
        + "\": {\"type\": \"keyword\"}\n"
        + "    }\n"
        + "}";

    /** Version of the interactions index schema */
    public final static Integer INTERACTIONS_INDEX_SCHEMA_VERSION = 1;
    /** Name of the conversational interactions index */
    public final static String INTERACTIONS_INDEX_NAME = ".conversational-interactions";
    /** Name of the interaction field for the conversation Id */
    public final static String INTERACTIONS_CONVERSATION_ID_FIELD = "conversation_id";
    /** Name of the interaction field for the human input */
    public final static String INTERACTIONS_INPUT_FIELD = "input";
    /** Name of the interaction field for the AI response */
    public final static String INTERACTIONS_RESPONSE_FIELD = "response";
    /** Name of the interaction field for the response's origin */
    public final static String INTERACTIONS_ORIGIN_FIELD = "origin";
    /** Name of the interaction field for the timestamp */
    public final static String INTERACTIONS_TIMESTAMP_FIELD = "timestamp";
    /** Mappings for the interactions index */
    public final static String INTERACTIONS_MAPPINGS = "{\n"
        + "    \"_meta\": {\n"
        + "        \"schema_version\": " + INTERACTIONS_INDEX_SCHEMA_VERSION + "\n" 
        + "    },\n"
        + "    \"properties\": {\n"
        + "        \""
        + INTERACTIONS_CONVERSATION_ID_FIELD
        + "\": {\"type\": \"keyword\"},\n"
        + "        \""
        + INTERACTIONS_TIMESTAMP_FIELD
        + "\": {\"type\": \"date\", \"format\": \"strict_date_optional_time||epoch_millis\"},\n"
        + "        \""
        + INTERACTIONS_INPUT_FIELD
        + "\": {\"type\": \"text\"},\n"
        + "        \""
        + INTERACTIONS_RESPONSE_FIELD
        + "\": {\"type\": \"text\"},\n"
        + "        \""
        + INTERACTIONS_ORIGIN_FIELD
        + "\": {\"type\": \"keyword\"},\n"
        + "        \""
        + USER_FIELD
        + "\": {\"type\": \"keyword\"}\n"
        + "    }\n"
        + "}";

}