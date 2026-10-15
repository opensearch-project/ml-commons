/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agenticsearch;

import java.util.List;
import java.util.Map;

import org.opensearch.core.action.ActionListener;

/**
 * Query-time access to registered agentic search templates, for callers outside the plugin
 * module (the template-fill tool in ml-algorithms). Implemented by the plugin's template service.
 */
public interface AgenticSearchTemplateResolver {

    /**
     * Fetch the templates the caller can fill for a search on {@code indexName}: registered, bound to an
     * index that {@code indexName} resolves within, and backed by a stored script the caller can read.
     * The access check runs before any schema reaches a model, so a template the caller could not
     * render is never offered.
     *
     * @param templateIds the template ids to look up
     * @param indexName the index, alias, or pattern being searched
     * @param listener yields the usable templates keyed by id; an unusable id is absent from the map
     */
    void getTemplates(List<String> templateIds, String indexName, ActionListener<Map<String, AgenticSearchTemplate>> listener);

    /**
     * Render a stored search template with the given params through the cluster's Mustache engine.
     *
     * @param templateId the {@code _scripts} template name
     * @param params the filled param values
     * @param listener yields the rendered {@code _search} body as a JSON string
     */
    void renderTemplate(String templateId, Map<String, Object> params, ActionListener<String> listener);
}
