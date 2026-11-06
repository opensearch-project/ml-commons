/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools.selection;

import static org.opensearch.ml.common.CommonValue.TOOL_INPUT_SCHEMA_FIELD;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.opensearch.OpenSearchTimeoutException;
import org.opensearch.action.support.ListenerTimeouts;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.utils.ToolUtils;
import org.opensearch.threadpool.ThreadPool;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

/**
 * The tool the model calls to discover tools it cannot see yet. Created per agent execution because
 * it appends matches to {@code promptTools}, the live map rendered into every LLM request.
 *
 * Tools are appended, never replaced, so the prompt prefix stays stable for provider-side prompt
 * caches. At most {@code max_dynamic_tools} are appended per run, counting direct-call adoptions.
 * Every outcome, including a timeout or embedding failure, is returned to the model as a tool
 * result; search must never fail an agent request.
 */
@Log4j2
public class SearchToolsTool implements Tool {

    public static final String NAME = "search_tools";
    public static final String TYPE = "SearchToolsTool";
    static final String QUERY_FIELD = "query";

    static final String INPUT_SCHEMA = "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\","
        + "\"description\":\"A few words describing what you need to do, for example: explain why a shard is unassigned\"}},"
        + "\"required\":[\"query\"],\"additionalProperties\":false}";

    static final String UNAVAILABLE_MESSAGE = "Tool search is unavailable right now. Continue with the tools you already have.";
    static final String NO_QUERY_MESSAGE = "Provide a short description of what you need to do in the 'query' field.";
    static final String NOTHING_LEFT_MESSAGE = "All available tools are already loaded. Continue with the tools you have.";
    static final String NO_MATCH_MESSAGE = "No matching tools found. Try different wording, or continue with the tools you have.";
    static final String LIMIT_MESSAGE = "Tool limit reached: no more tools can be added in this run. Continue with the tools you have.";

    /** Descriptions are echoed in the result only as a reminder; the full text is in the tool list. */
    private static final int RESULT_DESCRIPTION_CHARS = 200;
    /** Upper bound on hidden tool names spelled out in this tool's own description. */
    static final int MAX_LISTED_NAMES = 100;

    private final DynamicToolSelector selector;
    private final ToolSelectionSettings settings;
    private final String tenantId;
    private final Map<String, Tool> poolTools;
    private final Map<String, Tool> promptTools;
    /** Pinned tools and this tool; they don't count toward the cap. */
    private final Set<String> startingTools;
    private final ThreadPool threadPool;

    @Getter
    @Setter
    private String name = NAME;
    @Getter
    @Setter
    private String description;
    @Getter
    @Setter
    private Map<String, Object> attributes;

    /**
     * @param poolTools   every tool configured on the agent, excluding this one
     * @param promptTools the tools currently shown to the model; mutated by successful searches
     */
    public SearchToolsTool(
        DynamicToolSelector selector,
        ToolSelectionSettings settings,
        String tenantId,
        Map<String, Tool> poolTools,
        Map<String, Tool> promptTools,
        ThreadPool threadPool
    ) {
        this.selector = selector;
        this.settings = settings;
        this.tenantId = tenantId;
        this.poolTools = poolTools;
        this.promptTools = promptTools;
        this.startingTools = new HashSet<>(promptTools.keySet());
        this.startingTools.add(NAME);
        this.threadPool = threadPool;
        this.description = describe(hiddenToolNames(poolTools, promptTools));
        this.attributes = new HashMap<>();
        attributes.put(TOOL_INPUT_SCHEMA_FIELD, INPUT_SCHEMA);
        attributes.put("strict", false);
    }

    private static List<String> hiddenToolNames(Map<String, Tool> poolTools, Map<String, Tool> promptTools) {
        List<String> hidden = new ArrayList<>();
        for (String toolName : poolTools.keySet()) {
            if (!promptTools.containsKey(toolName)) {
                hidden.add(toolName);
            }
        }
        Collections.sort(hidden);
        return hidden;
    }

    /**
     * Lists the hidden tool names (sorted, capped at {@link #MAX_LISTED_NAMES}); with only a count,
     * models rarely searched. Fixed for the run and sorted so the description never changes and
     * prompt caches stay valid.
     */
    static String describe(List<String> hiddenNames) {
        StringBuilder text = new StringBuilder();
        text
            .append(
                String
                    .format(
                        Locale.ROOT,
                        "Find more tools. You can see only some of your tools; %d more are available but hidden to keep this prompt small",
                        hiddenNames.size()
                    )
            );
        if (!hiddenNames.isEmpty()) {
            int listed = Math.min(hiddenNames.size(), MAX_LISTED_NAMES);
            text.append(", including: ").append(String.join(", ", hiddenNames.subList(0, listed)));
            if (listed < hiddenNames.size()) {
                text.append(" and ").append(hiddenNames.size() - listed).append(" more");
            }
        }
        text
            .append(
                ". Call this whenever none of your visible tools fits the task, with a short description of what you need to do. "
                    + "The best matching tools are added to your tool list and can be called on your next step. "
                    + "If the results do not fit, search again with different wording."
            );
        return text.toString();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {
        String query = extractQuery(parameters);
        if (query == null) {
            listener.onResponse((T) NO_QUERY_MESSAGE);
            return;
        }
        if (remainingCapacity() <= 0) {
            log.info("tool_selection search limit_reached=true max_dynamic_tools={}", settings.getMaxDynamicTools());
            listener.onResponse((T) LIMIT_MESSAGE);
            return;
        }
        Map<String, Tool> candidates = hiddenTools();
        if (candidates.isEmpty()) {
            listener.onResponse((T) NOTHING_LEFT_MESSAGE);
            return;
        }

        long startNanos = System.nanoTime();
        ActionListener<List<String>> onRanked = ActionListener.wrap(names -> {
            listener.onResponse((T) addToPrompt(names, candidates, startNanos));
        }, e -> {
            long latencyMillis = (System.nanoTime() - startNanos) / 1_000_000L;
            if (e instanceof OpenSearchTimeoutException) {
                log.warn("tool_selection search timed_out=true latency_ms={} timeout={}", latencyMillis, settings.getSearchTimeout());
            } else {
                log.warn("tool_selection search failed=true latency_ms={}", latencyMillis, e);
            }
            listener.onResponse((T) UNAVAILABLE_MESSAGE);
        });

        ActionListener<List<String>> timed = null;
        try {
            timed = ListenerTimeouts.wrapWithTimeout(threadPool, onRanked, settings.getSearchTimeout(), ThreadPool.Names.GENERIC, NAME);
            selector.search(query, candidates, settings, tenantId, timed);
        } catch (Exception e) {
            // Fail through the wrapper so the scheduled timeout can't answer the model a second time.
            (timed != null ? timed : onRanked).onFailure(e);
        }
    }

    /** Accepts {"query": "..."}, falling back to the raw input when the model passed plain text. */
    private String extractQuery(Map<String, String> parameters) {
        Map<String, String> extracted = ToolUtils.extractInputParameters(parameters, attributes);
        String query = extracted.get(QUERY_FIELD);
        if (query == null || query.isBlank()) {
            String raw = parameters.get("input");
            query = raw != null && !raw.trim().startsWith("{") ? raw : null;
        }
        return query == null || query.isBlank() ? null : query.trim();
    }

    private Map<String, Tool> hiddenTools() {
        Map<String, Tool> hidden = new LinkedHashMap<>();
        for (Map.Entry<String, Tool> entry : poolTools.entrySet()) {
            if (!promptTools.containsKey(entry.getKey())) {
                hidden.put(entry.getKey(), entry.getValue());
            }
        }
        return hidden;
    }

    /**
     * Appends a hidden tool the model called directly, so its schema is shown from the next step.
     * Counts against the cap. The call itself runs regardless; hiding is not access control.
     *
     * @return true if the tool was appended
     */
    public boolean adoptDirectCall(String toolName) {
        if (toolName == null || promptTools.containsKey(toolName) || !poolTools.containsKey(toolName)) {
            return false;
        }
        boolean adopted = remainingCapacity() > 0;
        if (adopted) {
            promptTools.put(toolName, poolTools.get(toolName));
        }
        log.info("tool_selection direct_call tool={} adopted={}", toolName, adopted);
        return adopted;
    }

    /** How many more tools may be appended in this run. */
    private int remainingCapacity() {
        int appended = 0;
        for (String toolName : promptTools.keySet()) {
            if (!startingTools.contains(toolName)) {
                appended++;
            }
        }
        return settings.getMaxDynamicTools() - appended;
    }

    private String addToPrompt(List<String> names, Map<String, Tool> candidates, long startNanos) {
        StringBuilder result = new StringBuilder();
        int added = 0;
        int capacity = remainingCapacity();
        for (String toolName : names) {
            Tool tool = candidates.get(toolName);
            if (tool == null) {
                continue;
            }
            if (added >= capacity) {
                break;
            }
            promptTools.putIfAbsent(toolName, tool);
            result.append("\n- ").append(toolName).append(": ").append(shorten(tool.getDescription()));
            added++;
        }
        long latencyMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        // The query is user-derived text, so it is not logged; tool names are configuration.
        log.info("tool_selection search found={} latency_ms={} tools={}", added, latencyMillis, names);
        if (added == 0) {
            return NO_MATCH_MESSAGE;
        }
        String found = "Found " + added + " tool(s), now available to call:" + result;
        return remainingCapacity() <= 0 ? found + "\n" + LIMIT_MESSAGE : found;
    }

    private static String shorten(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= RESULT_DESCRIPTION_CHARS ? oneLine : oneLine.substring(0, RESULT_DESCRIPTION_CHARS) + "...";
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getVersion() {
        return null;
    }

    @Override
    public boolean validate(Map<String, String> parameters) {
        return true;
    }
}
