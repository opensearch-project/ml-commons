/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.ml.action.MLCommonsIntegTestCase;
import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.test.OpenSearchIntegTestCase;

@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE, numDataNodes = 2)
public class GetModelGroupITTests extends MLCommonsIntegTestCase {
    private String irisIndexName;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private String modelGroupId;

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @Ignore
    public void testGetModel_IndexNotFound() {
        exceptionRule.expect(MLResourceNotFoundException.class);
        MLModelGroup modelGroup = getModelGroup("test_id");
    }

    public void testGetModel_NullModelIdException() {
        exceptionRule.expect(ActionRequestValidationException.class);
        MLModelGroup modelGroup = getModelGroup(null);
    }
}
