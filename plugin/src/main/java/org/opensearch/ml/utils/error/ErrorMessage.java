/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils.error;

import java.util.HashMap;
import java.util.Map;

import org.opensearch.core.rest.RestStatus;
import org.opensearch.transport.ActionTransportException;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.SneakyThrows;

/** Error Message. */
public class ErrorMessage {

    protected Throwable exception;

    @Getter
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
        return exception == null ? "NULL" : exception.getClass().getSimpleName();
    }

    protected String fetchReason() {
        return status == RestStatus.BAD_REQUEST.getStatus() ? "Invalid Request" : "System Error";
    }

    protected String fetchDetails() {
        if (exception == null) {
            return "NULL";
        }

        final String msg;
        // Prevent the method from exposing internal information such as internal ip address etc. that is a security concern.
        if (hasInternalInformation(exception)) {
            msg = decorateMessage(exception);
        } else {
            msg = exception.getLocalizedMessage();
        }

        return emptyStringIfNull(msg);
    }

    private String emptyStringIfNull(String str) {
        return str != null ? str : "";
    }

    private Boolean hasInternalInformation(Throwable t) {
        if (t instanceof ActionTransportException) {
            return true;
        }
        return false;
    }

    private String decorateMessage(Throwable t) {
        if (t instanceof ActionTransportException) {
            String regexIPv4 = "\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(:\\d{1,5})?";
            String regexIPv6 = "\\[?((?:[\\da-fA-F]{0,4}:[\\da-fA-F]{0,4}){2,7})(?:[\\/\\\\%](\\d{1,3}))?\\]?(:\\d{1,5})?";
            return emptyStringIfNull(t.getLocalizedMessage()).replaceAll(regexIPv4, "x.x.x.x:x").replaceAll(regexIPv6, "x.x.x.x.x.x:x");
        }
        return "";
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
