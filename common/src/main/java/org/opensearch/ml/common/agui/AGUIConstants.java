/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agui;

/**
 * Constants for AG-UI implementation.
 * 
 * Naming Conventions:
 *   AGUI_ROLE_* - Message role identifiers
 *   AGUI_PARAM_* - Internal parameter keys
 *   AGUI_FIELD_* - External API field names
 *   AGUI_EVENT_* - Event type identifiers
 *   AGUI_PREFIX_* - ID prefixes for generated identifiers
 */
public final class AGUIConstants {

    // ========== Message Roles ==========

    /** Role identifier for assistant messages */
    public static final String AGUI_ROLE_ASSISTANT = "assistant";

    /** Role identifier for user messages */
    public static final String AGUI_ROLE_USER = "user";

    /** Role identifier for tool result messages */
    public static final String AGUI_ROLE_TOOL = "tool";

    // ========== Parameter Keys (Internal) ==========

    /** Parameter key for AG-UI thread identifier */
    public static final String AGUI_PARAM_THREAD_ID = "agui_thread_id";

    /** Parameter key for AG-UI run identifier */
    public static final String AGUI_PARAM_RUN_ID = "agui_run_id";

    /** Parameter key for AG-UI messages array */
    public static final String AGUI_PARAM_MESSAGES = "agui_messages";

    /** Parameter key for AG-UI tools array */
    public static final String AGUI_PARAM_TOOLS = "agui_tools";

    /** Parameter key for AG-UI context array */
    public static final String AGUI_PARAM_CONTEXT = "agui_context";

    /** Parameter key for AG-UI state object */
    public static final String AGUI_PARAM_STATE = "agui_state";

    /** Parameter key for AG-UI forwarded properties */
    public static final String AGUI_PARAM_FORWARDED_PROPS = "agui_forwarded_props";

    /** Parameter key for AG-UI tool call results */
    public static final String AGUI_PARAM_TOOL_CALL_RESULTS = "agui_tool_call_results";

    /** Parameter key for AG-UI assistant tool call messages */
    public static final String AGUI_PARAM_ASSISTANT_TOOL_CALL_MESSAGES = "agui_assistant_tool_call_messages";

    /** Parameter key for backend tool names (used for filtering) */
    public static final String AGUI_PARAM_BACKEND_TOOL_NAMES = "backend_tool_names";

    /** Parameter key for current AG-UI message id */
    public static final String AGUI_PARAM_MESSAGE_ID = "agui_message_id";

    /** Parameter key for AG-UI text message started flag */
    public static final String AGUI_PARAM_TEXT_MESSAGE_STARTED = "agui_text_message_started";

    // ========== Field Names (External API) ==========

    /** Field name for thread identifier in AG-UI input */
    public static final String AGUI_FIELD_THREAD_ID = "threadId";

    /** Field name for run identifier in AG-UI input */
    public static final String AGUI_FIELD_RUN_ID = "runId";

    /** Field name for messages array in AG-UI input */
    public static final String AGUI_FIELD_MESSAGES = "messages";

    /** Field name for tools array in AG-UI input */
    public static final String AGUI_FIELD_TOOLS = "tools";

    /** Field name for context array in AG-UI input */
    public static final String AGUI_FIELD_CONTEXT = "context";

    /** Field name for state object in AG-UI input */
    public static final String AGUI_FIELD_STATE = "state";

    /** Field name for forwarded properties in AG-UI input */
    public static final String AGUI_FIELD_FORWARDED_PROPS = "forwardedProps";

    /** Field name for message role */
    public static final String AGUI_FIELD_ROLE = "role";

    /** Field name for message content */
    public static final String AGUI_FIELD_CONTENT = "content";

    /** Field name for tool call identifier */
    public static final String AGUI_FIELD_TOOL_CALL_ID = "toolCallId";

    /** Field name for tool calls array */
    public static final String AGUI_FIELD_TOOL_CALLS = "toolCalls";

    /** Field name for message identifier */
    public static final String AGUI_FIELD_ID = "id";

    /** Field name for tool call type */
    public static final String AGUI_FIELD_TYPE = "type";

    /** Field name for function object in tool calls */
    public static final String AGUI_FIELD_FUNCTION = "function";

    /** Field name for function name */
    public static final String AGUI_FIELD_NAME = "name";

    /** Field name for function arguments */
    public static final String AGUI_FIELD_ARGUMENTS = "arguments";
}
