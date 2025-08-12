/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

/**
 * Helper class to store fact search results
 */
public class FactSearchResult {
    private final String id;
    private final String text;
    private final float score;

    public FactSearchResult(String id, String text, float score) {
        this.id = id;
        this.text = text;
        this.score = score;
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public float getScore() {
        return score;
    }
}
