/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.ml.action.MLCommonsIntegTestCase;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.test.OpenSearchIntegTestCase;

@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE, numDataNodes = 2)
public class GetModelITTests extends MLCommonsIntegTestCase {
    private String irisIndexName;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        super.setUp();
        irisIndexName = "iris_data_for_model_it";
        loadIrisData(irisIndexName);
    }

    @Ignore
    public void testGetModel_IndexNotFound() {
        exceptionRule.expect(MLResourceNotFoundException.class);
        MLModel model = getModel("test_id");
    }

    public void testGetModel_NullModelIdException() {
        exceptionRule.expect(ActionRequestValidationException.class);
        MLModel model = getModel(null);
    }
}
