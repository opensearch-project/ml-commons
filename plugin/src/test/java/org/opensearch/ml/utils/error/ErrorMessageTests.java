/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils.error;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.opensearch.core.rest.RestStatus.BAD_REQUEST;
import static org.opensearch.core.rest.RestStatus.SERVICE_UNAVAILABLE;

import org.junit.Test;

public class ErrorMessageTests {

    @Test
    public void fetchReason() {
        ErrorMessage errorMessage = new ErrorMessage(new IllegalStateException("illegal state"), SERVICE_UNAVAILABLE.getStatus());

        assertEquals(errorMessage.fetchReason(), "System Error");
    }

    @Test
    public void fetchDetails() {
        ErrorMessage errorMessage = new ErrorMessage(new IllegalStateException("illegal state"), SERVICE_UNAVAILABLE.getStatus());

        assertEquals(errorMessage.fetchDetails(), "illegal state");
    }

    @Test
    public void testToString() {
        ErrorMessage errorMessage = new ErrorMessage(new IllegalStateException("illegal state"), SERVICE_UNAVAILABLE.getStatus());
        assertEquals(
            "{\n"
                + "  \"error\": {\n"
                + "    \"reason\": \"System Error\",\n"
                + "    \"details\": \"illegal state\",\n"
                + "    \"type\": \"IllegalStateException\"\n"
                + "  },\n"
                + "  \"status\": 503\n"
                + "}",
            errorMessage.toString()
        );
    }

    @Test
    public void testBadRequestToString() {
        ErrorMessage errorMessage = new ErrorMessage(new IllegalStateException(), BAD_REQUEST.getStatus());
        assertEquals(
            "{\n"
                + "  \"error\": {\n"
                + "    \"reason\": \"Invalid Request\",\n"
                + "    \"details\": \"\",\n"
                + "    \"type\": \"IllegalStateException\"\n"
                + "  },\n"
                + "  \"status\": 400\n"
                + "}",
            errorMessage.toString()
        );
    }

    @Test
    public void testToStringWithEmptyErrorMessage() {
        ErrorMessage errorMessage = new ErrorMessage(new IllegalStateException(), SERVICE_UNAVAILABLE.getStatus());
        assertEquals(
            "{\n"
                + "  \"error\": {\n"
                + "    \"reason\": \"System Error\",\n"
                + "    \"details\": \"\",\n"
                + "    \"type\": \"IllegalStateException\"\n"
                + "  },\n"
                + "  \"status\": 503\n"
                + "}",
            errorMessage.toString()
        );
    }

    @Test
    public void getType() {
        ErrorMessage errorMessage = new ErrorMessage(new IllegalStateException("illegal state"), SERVICE_UNAVAILABLE.getStatus());

        assertEquals(errorMessage.getType(), "IllegalStateException");
    }

    @Test
    public void getReason() {
        ErrorMessage errorMessage = new ErrorMessage(new IllegalStateException("illegal state"), SERVICE_UNAVAILABLE.getStatus());

        assertEquals(errorMessage.getReason(), "System Error");
    }

    @Test
    public void getDetails() {
        ErrorMessage errorMessage = new ErrorMessage(new IllegalStateException("illegal state"), SERVICE_UNAVAILABLE.getStatus());

        assertEquals(errorMessage.getDetails(), "illegal state");
    }
}
