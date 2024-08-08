/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.exception;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MLExceptionTest {

    @Test
    public void testConstructor_ErrorMessage() {
        String message = "test";
        MLException exception = new MLException(message);
        assertEquals(message, exception.getMessage());
        assertTrue(exception.isCountedInStats());
        exception.countedInStats(false);
        assertFalse(exception.isCountedInStats());
    }

    @Test
    public void testConstructor_Cause() {
        String message = "test";
        Throwable cause = new RuntimeException(message);
        MLException exception = new MLException(cause);
        assertEquals(cause, exception.getCause());
        assertTrue(exception.isCountedInStats());
    }

    @Test
    public void testConstructor_MessageAndCause() {
        String message = "test";
        Throwable cause = new RuntimeException(message);
        MLException exception = new MLException(message, cause);
        assertEquals(cause, exception.getCause());
        assertEquals(message, exception.getMessage());
        assertTrue(exception.isCountedInStats());
    }
}
