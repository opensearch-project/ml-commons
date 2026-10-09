/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.compat;

/**
 * Replays ML documents stored by earlier OpenSearch releases against the code on this branch.
 * Fixtures: {@code compat-fixtures/opensearch}, one directory per release, captured from a throwaway
 * single-node cluster with dummy credentials. Add a set for every release.
 */
public class MLStoredDocumentCompatibilityTests extends StoredDocumentCompatibilityTestCase {

    @Override
    protected String fixturesResource() {
        return "/compat-fixtures/opensearch";
    }
}
