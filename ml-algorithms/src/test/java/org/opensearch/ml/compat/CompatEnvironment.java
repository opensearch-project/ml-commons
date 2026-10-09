/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.compat;

import java.util.List;
import java.util.Set;

import org.opensearch.common.settings.Settings;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.engine.algorithms.remote.RemoteConnectorExecutor;

/**
 * What a distribution changes about how stored ML documents are validated and invoked. The defaults are plain
 * OpenSearch; a distribution that adds connector protocols, credential sources or endpoint allow-lists overrides
 * the relevant methods in its own {@link StoredDocumentCompatibilityTestCase} subclass.
 */
public interface CompatEnvironment {

    /** Allow-list a connector must match to be created or registered (the {@code validate-endpoint} step). */
    default List<String> registrationTrustedEndpoints() {
        return MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.get(Settings.EMPTY);
    }

    /** {@code plugins.ml_commons.trusted_connector_endpoints_regex} as the executor sees it at predict time. */
    default List<String> predictTrustedEndpoints() {
        return MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.get(Settings.EMPTY);
    }

    /**
     * Replace anything other than the HTTP call that predict reaches out to (credential services, secret stores).
     * Runs on the test thread around deploy and predict; the returned handle is closed afterwards.
     */
    default AutoCloseable stubExternalServices(MLModel model) {
        return () -> {};
    }

    /** Last chance to adjust the deployed executor before predict, e.g. to stub endpoint resolution. */
    default RemoteConnectorExecutor prepareExecutor(RemoteConnectorExecutor executor, MLModel model) {
        return executor;
    }

    /** Connector protocols whose outgoing request must carry a SigV4 {@code Authorization} header. */
    default Set<String> sigV4Protocols() {
        return Set.of("aws_sigv4");
    }
}
