/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import java.io.IOException;
import java.util.Map;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.Logger;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.exception.MLLimitExceededException;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;

public class MLExceptionUtils {

    public static final String NOT_SERIALIZABLE_EXCEPTION_WRAPPER = "NotSerializableExceptionWrapper: ";
    public static final String REMOTE_INFERENCE_DISABLED_ERR_MSG =
        "Remote Inference is currently disabled. To enable it, update the setting \"plugins.ml_commons.remote_inference_enabled\" to true.";
    public static final String UPDATE_CONNECTOR_DISABLED_ERR_MSG =
        "Update connector API is currently disabled. To enable it, update the setting \"plugins.ml_commons.update_connector.enabled\" to true.";

    public static String getRootCauseMessage(final Throwable throwable) {
        String message = ExceptionUtils.getRootCauseMessage(throwable);
        if (message != null && message.startsWith(NOT_SERIALIZABLE_EXCEPTION_WRAPPER)) {
            message = message.replace(NOT_SERIALIZABLE_EXCEPTION_WRAPPER, "");
        }
        message = message.substring(message.indexOf(":") + 2);
        return message;
    }

    public static String toJsonString(Map<String, String> nodeErrors) throws IOException {
        if (nodeErrors == null || nodeErrors.size() == 0) {
            return null;
        }
        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder.startObject();
        for (Map.Entry<String, String> entry : nodeErrors.entrySet()) {
            builder.field(entry.getKey(), entry.getValue());
        }
        builder.endObject();
        return builder.toString();
    }

    public static void logException(String errorMessage, Exception e, Logger log) {
        Throwable rootCause = ExceptionUtils.getRootCause(e);
        if (e instanceof MLLimitExceededException || e instanceof MLResourceNotFoundException || e instanceof IllegalArgumentException) {
            log.warn(e.getMessage());
        } else if (rootCause instanceof MLLimitExceededException
            || rootCause instanceof MLResourceNotFoundException
            || rootCause instanceof IllegalArgumentException) {
            log.warn(rootCause.getMessage());
        } else {
            log.error(errorMessage, e);
        }
    }
}
