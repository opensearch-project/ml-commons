/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.exception;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class MLResourceNotFoundExceptionTest {

    @Test
    public void testConstructor_ErrorMessage() {
        String message = "test";
        MLException exception = new MLResourceNotFoundException(message);
        assertEquals(message, exception.getMessage());
        assertFalse(exception.isCountedInStats());
    }

    @Test
    public void testConstructor_Cause() {
        String message = "test";
        Throwable cause = new RuntimeException(message);
        MLException exception = new MLResourceNotFoundException(cause);
        assertEquals(cause, exception.getCause());
        assertFalse(exception.isCountedInStats());
    }
}
