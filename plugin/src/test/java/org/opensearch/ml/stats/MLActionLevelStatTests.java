/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats;

import static org.opensearch.ml.stats.MLActionLevelStat.ML_ACTION_REQUEST_COUNT;

import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.test.OpenSearchTestCase;

public class MLActionLevelStatTests extends OpenSearchTestCase {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    public void testValidStat() {
        assertEquals(ML_ACTION_REQUEST_COUNT, MLActionLevelStat.from(ML_ACTION_REQUEST_COUNT.name()));
    }

    public void testInvalidStat() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Wrong ML action level stat");
        MLActionLevelStat.from("abc");
    }
}
