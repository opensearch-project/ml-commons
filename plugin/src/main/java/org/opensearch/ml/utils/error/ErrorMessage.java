/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils.error;

import java.util.HashMap;
import java.util.Map;

import org.opensearch.core.rest.RestStatus;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.SneakyThrows;

/** Error Message. */
public class ErrorMessage {

    protected Throwable exception;

    private final int status;

    @Getter
    private final String type;

    @Getter
    private final String reason;

    @Getter
    private final String details;

    /** Error Message Constructor. */
    public ErrorMessage(Throwable exception, int status) {
        this.exception = exception;
        this.status = status;

        this.type = fetchType();
        this.reason = fetchReason();
        this.details = fetchDetails();
    }

    private String fetchType() {
        return exception.getClass().getSimpleName();
    }

    protected String fetchReason() {
        return status == RestStatus.BAD_REQUEST.getStatus() ? "Invalid Request" : "System Error";
    }

    protected String fetchDetails() {
        // Some exception prints internal information (full class name) which is security concern
        return emptyStringIfNull(exception.getLocalizedMessage());
    }

    private String emptyStringIfNull(String str) {
        return str != null ? str : "";
    }

    @SneakyThrows
    @Override
    public String toString() {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> errorContent = new HashMap<>();
        errorContent.put("type", type);
        errorContent.put("reason", reason);
        errorContent.put("details", details);
        Map<String, Object> errMessage = new HashMap<>();
        errMessage.put("status", status);
        errMessage.put("error", errorContent);

        return objectMapper.writeValueAsString(errMessage);
    }
}
