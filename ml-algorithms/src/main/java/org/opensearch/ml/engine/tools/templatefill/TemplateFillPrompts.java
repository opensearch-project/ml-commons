/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools.templatefill;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.opensearch.ml.common.agenticsearch.AgenticSearchTemplate;

/**
 * Prompts for search-template fill. The model is handed a forced tool whose fields are the template's
 * parameters, so the prompts carry no DSL rules or mapping: the typed tool schema (per-field
 * descriptions and allowed values) carries the guidance.
 *
 * <p>The abstain wording in these prompts and in {@link FillToolSchema} was tuned by prompt sweeps in
 * the agent server: vaguer or heavier wordings over-abstained. Re-evaluate before editing. The one
 * departure is how the multi-template prompt names parameter prefixes, which here are per-template tags
 * listed with the candidates rather than template ids.
 */
public final class TemplateFillPrompts {

    private TemplateFillPrompts() {}

    public static final String FILL_TOOL_NAME = "FillTemplate";
    public static final String FILL_TOOL_DESCRIPTION = "Fill the search template's parameters for the user's question.";

    public static final String FILL_SYSTEM_PROMPT = "You extract search parameters from a user's question to fill a predefined "
        + "OpenSearch search template.\n"
        + "Call the FillTemplate tool exactly once. Fill only the parameters the "
        + "question clearly implies; leave everything else unset — do not guess.\n"
        + "Put ONLY content/topic words in any free-text query parameter — never counts, "
        + "filters, sort terms, or field names.\n"
        + "For enum parameters, choose only from the options that parameter allows.\n"
        + "If the question needs something these parameters cannot express — a field not "
        + "listed here, prefix/wildcard/fuzzy matching, aggregations, an unsupported range, "
        + "or custom scoring — set cannot_express=true and leave the other parameters unset. "
        + "Do not force an approximate fill; abstaining routes the question to a more capable "
        + "path.";

    public static final String MULTI_FILL_TOOL_NAME = "SelectAndFillTemplate";
    public static final String MULTI_FILL_TOOL_DESCRIPTION = "Choose the best-fitting search template and fill its parameters.";

    // The expressibility check comes before the choice: asked to choose first, the model commits to a
    // template and fills it regardless, so a question no template can express yields a wrong query.
    public static final String MULTI_FILL_SYSTEM_PROMPT = "You answer a user's search question using one of several predefined OpenSearch "
        + "search templates.\n"
        + "Call the tool exactly once. Work in this order:\n"
        + "1. First decide whether any candidate template can express the question. Set "
        + "cannot_express=true when none can, and stop — do not fill parameters.\n"
        + "2. Only if one can, choose that template and fill its parameters.\n"
        + "Abstaining is the correct answer whenever the question needs a field, filter, "
        + "projection, ranking signal, aggregation, count-only answer, exact phrase, "
        + "prefix/wildcard/fuzzy match, or similarity that no candidate's parameters cover. "
        + "Abstaining routes the question to a more capable path, so a near-miss fill is worse "
        + "than declining.\n"
        + "When you do fill: fill only the parameters the question clearly implies and leave "
        + "the rest unset — do not guess. Put ONLY content/topic words in a free-text query "
        + "parameter — never counts, filters, sort terms, or field names. For enum parameters, "
        + "choose only from the options that parameter allows. Each parameter name is prefixed "
        + "with the tag of the template it belongs to, shown in the candidate list; fill only "
        + "parameters carrying the prefix of the template you chose.";

    public static String fillUserPrompt(String question) {
        return "Question: " + question + "\n\nFill the FillTemplate tool's parameters for this question.\n";
    }

    /**
     * @param prefixes template id to the prefix its params carry in the merged tool schema
     */
    public static String multiFillUserPrompt(String question, List<AgenticSearchTemplate> candidates, Map<String, String> prefixes) {
        String listed = candidates
            .stream()
            .map(
                c -> "- "
                    + c.getTemplateId()
                    + " (parameters prefixed "
                    + prefixes.get(c.getTemplateId())
                    + "): "
                    + (isBlank(c.getDescription()) ? "(no description)" : c.getDescription())
            )
            .collect(Collectors.joining("\n"));
        return "Question: "
            + question
            + "\n\nCandidate templates:\n"
            + listed
            + "\n\nChoose the best template and fill its parameters for this question.\n";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
