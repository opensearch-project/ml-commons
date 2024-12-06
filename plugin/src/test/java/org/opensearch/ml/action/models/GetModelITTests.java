/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import org.opensearch.OpenSearchTimeoutException;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.ml.action.MLCommonsIntegTestCase;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.test.OpenSearchIntegTestCase;

@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE, numDataNodes = 2)
public class GetModelITTests extends MLCommonsIntegTestCase {

    private static final int MAX_RETRIES = 3;

    public void testGetModel_IndexNotFound() {
        testGetModelExceptionsWithRetry(MLResourceNotFoundException.class, "test_id");
    }

    public void testGetModel_NullModelIdException() {
        testGetModelExceptionsWithRetry(ActionRequestValidationException.class, null);
    }

    private void testGetModelExceptionsWithRetry(Class<? extends Exception> expectedException, String modelId) {
        assertThrows(expectedException, () -> {
            for (int retryAttempt = 1; retryAttempt <= MAX_RETRIES; retryAttempt++) {
                try {
                    getModel(modelId);
                    return;
                } catch (OpenSearchTimeoutException e) {
                    logger.info("GetModelITTests attempt: {}", retryAttempt);

                    if (retryAttempt == MAX_RETRIES) {
                        logger.error("Failed to execute test GetModelITTests after {} retries due to timeout", MAX_RETRIES);
                        throw e;
                    }

                    // adding small delay between retries
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Thread was interrupted during retry", ie);
                    }
                }
            }
        });
    }
}
