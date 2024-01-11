/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.common.io.stream.NotSerializableExceptionWrapper;
import org.opensearch.ml.common.exception.MLLimitExceededException;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.test.OpenSearchIntegTestCase;

public class MLExceptionUtilsTests extends OpenSearchIntegTestCase {

    @Mock
    private Logger logger;

    @Before
    public void setup() throws URISyntaxException {
        MockitoAnnotations.openMocks(this);
    }

    public void testGetRootCauseMessage() {
        String error = randomAlphaOfLength(10);
        Throwable exception = new MLLimitExceededException(error);
        String rootCauseMessage = MLExceptionUtils.getRootCauseMessage(exception);
        assertEquals(error, rootCauseMessage);
    }

    public void testGetRootCauseMessage_NotSerializableException() {
        String error = randomAlphaOfLength(10);
        Exception exception = new MLLimitExceededException(error);
        Throwable throwable = new NotSerializableExceptionWrapper(exception);
        String rootCauseMessage = MLExceptionUtils.getRootCauseMessage(throwable);
        assertEquals(error, rootCauseMessage);
    }

    public void testToJsonString_NullNodeError() throws IOException {
        String str = MLExceptionUtils.toJsonString(null);
        assertNull(str);
    }

    public void testToJsonString_EmptyNodeError() throws IOException {
        String str = MLExceptionUtils.toJsonString(new HashMap<>());
        assertNull(str);
    }

    public void testToJsonString() throws IOException {
        String str = MLExceptionUtils.toJsonString(Map.of("node1", "error1"));
        assertEquals("{\"node1\":\"error1\"}", str);
    }

    public void testLogException_MLLimitExceededException() {
        String error = randomAlphaOfLength(10);
        Exception exception = new MLLimitExceededException(error);
        testLogException(error, exception);
    }

    public void testLogException_MLResourceNotFoundException() {
        String error = randomAlphaOfLength(10);
        Exception exception = new MLResourceNotFoundException(error);
        testLogException(error, exception);
    }

    public void testLogException_RootCause_MLLimitExceededException() {
        String error = randomAlphaOfLength(10);
        Exception exception = new MLLimitExceededException(error);
        testLogException_RootCause(error, exception);
    }

    public void testLogException_RootCause_MLResourceNotFoundException() {
        String error = randomAlphaOfLength(10);
        Exception exception = new MLLimitExceededException(error);
        testLogException_RootCause(error, exception);
    }

    private void testLogException(String error, Exception exception) {
        MLExceptionUtils.logException(randomAlphaOfLength(10), exception, logger);
        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(logger).warn(argumentCaptor.capture());
        assertEquals(error, argumentCaptor.getValue());
    }

    private void testLogException_RootCause(String error, Exception rootCause) {
        Exception notSerializableException = new RuntimeException(rootCause);
        MLExceptionUtils.logException(randomAlphaOfLength(10), notSerializableException, logger);
        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(logger).warn(argumentCaptor.capture());
        assertEquals(error, argumentCaptor.getValue());
    }
}
