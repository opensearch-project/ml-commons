/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import java.io.IOException;
import java.util.concurrent.CompletionException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SdkClientUtilsTests {

    @Mock
    private OpenSearchStatusException testException;
    @Mock
    private InterruptedException interruptedException;
    @Mock
    private IOException ioException;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testUnwrapAndConvertToRuntime() {
        CompletionException ce = new CompletionException(testException);
        RuntimeException rte = SdkClientUtils.unwrapAndConvertToRuntime(ce);
        assertSame(testException, rte);

        ce = new CompletionException(interruptedException);
        rte = SdkClientUtils.unwrapAndConvertToRuntime(ce); // sets interrupted
        assertTrue(Thread.interrupted()); // tests and resets interrupted
        assertTrue(rte instanceof OpenSearchException);
        assertSame(interruptedException, rte.getCause());

        ce = new CompletionException(ioException);
        rte = SdkClientUtils.unwrapAndConvertToRuntime(ce);
        assertFalse(Thread.currentThread().isInterrupted());
        assertTrue(rte instanceof OpenSearchException);
        assertSame(ioException, rte.getCause());
    }
}
