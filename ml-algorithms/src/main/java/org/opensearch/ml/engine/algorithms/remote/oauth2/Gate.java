/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.oauth2;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_VERTEXAI_CONNECTOR_DISABLED_MESSAGE;

import java.util.function.BooleanSupplier;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;

/**
 * The cluster setting, or settings, a token request and the token it obtains are subject to, read live: before a
 * credential is read, before the token request is sent, when its token arrives and whenever a handle's value is read.
 * Closed, it names the setting that is off. Which setting that is depends on the connector: {@code connector.auth_enabled}
 * for an {@code auth_config} a request wrote, {@code connector.vertexai_enabled} for a legacy {@code google_cloud} identity.
 */
@FunctionalInterface
public interface Gate {
    /** The 403 refusal while the gate is closed; null while it is open. */
    OpenSearchStatusException refusal();

    /** A gate on one setting, closed with {@code disabledMessage} while {@code enabled} is false. */
    static Gate of(BooleanSupplier enabled, String disabledMessage) {
        return () -> enabled.getAsBoolean() ? null : new OpenSearchStatusException(disabledMessage, RestStatus.FORBIDDEN);
    }

    /** A gate open while both are; closed, it reports the first of the two that is. */
    static Gate both(Gate first, Gate second) {
        return () -> {
            OpenSearchStatusException refusal = first.refusal();
            return refusal != null ? refusal : second.refusal();
        };
    }

    /** The reason a log line gives for {@code refusal}: which setting is off, read from the refusal itself. */
    static String labelOf(OpenSearchStatusException refusal) {
        if (ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE.equals(refusal.getMessage())) {
            return "auth_disabled";
        }
        if (ML_COMMONS_VERTEXAI_CONNECTOR_DISABLED_MESSAGE.equals(refusal.getMessage())) {
            return "vertexai_disabled";
        }
        return "gate_closed";
    }
}
