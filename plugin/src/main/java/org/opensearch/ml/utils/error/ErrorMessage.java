/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils.error;

import java.util.HashMap;
import java.util.Map;

import org.opensearch.core.rest.RestStatus;
import org.opensearch.transport.ActionTransportException;

import lombok.Getter;
import lombok.SneakyThrows;
import tools.jackson.databind.ObjectMapper;

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
        return exception == null ? "Unknown Exception" : exception.getClass().getSimpleName();
    }

    protected String fetchReason() {
        if (status == RestStatus.BAD_REQUEST.getStatus()) {
            return "Invalid Request";
        }
        if (status > RestStatus.BAD_REQUEST.getStatus() && status < RestStatus.INTERNAL_SERVER_ERROR.getStatus()) {
            return clientErrorReason(status);
        }
        return "System Error";
    }

    /** Standard HTTP reason phrase for a 4xx status other than 400. */
    private static String clientErrorReason(int status) {
        switch (status) {
            case 401:
                return "Unauthorized";
            case 402:
                return "Payment Required";
            case 403:
                return "Forbidden";
            case 404:
                return "Not Found";
            case 405:
                return "Method Not Allowed";
            case 406:
                return "Not Acceptable";
            case 407:
                return "Proxy Authentication Required";
            case 408:
                return "Request Timeout";
            case 409:
                return "Conflict";
            case 410:
                return "Gone";
            case 411:
                return "Length Required";
            case 412:
                return "Precondition Failed";
            case 413:
                return "Request Entity Too Large";
            case 414:
                return "Request-URI Too Long";
            case 415:
                return "Unsupported Media Type";
            case 416:
                return "Requested Range Not Satisfiable";
            case 417:
                return "Expectation Failed";
            case 421:
                return "Misdirected Request";
            case 422:
                return "Unprocessable Entity";
            case 423:
                return "Locked";
            case 424:
                return "Failed Dependency";
            case 429:
                return "Too Many Requests";
            default:
                return "Client Error";
        }
    }

    protected String fetchDetails() {
        if (exception == null) {
            return "No Exception Details";
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
