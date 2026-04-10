/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc.interfaces;

import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;

/**
 * Interface for model access control validation needed by gRPC services.
 * This abstracts access control logic to avoid circular dependencies between grpc and plugin modules.
 */
public interface MLModelAccessControlHelper {

    /**
     * Validates whether a user has access to a model group.
     *
     * @param user the user requesting access
     * @param mlFeatureEnabledSetting feature flags for ML capabilities
     * @param tenantId optional tenant ID for multi-tenancy
     * @param modelGroupId the model group to validate access for
     * @param actionName the action being performed (e.g., MLPredictionStreamTaskAction.NAME)
     * @param client OpenSearch client for validation queries
     * @param sdkClient SDK client for multi-tenant operations
     * @param listener callback with validation result (true if access granted, false otherwise)
     */
    void validateModelGroupAccess(
        User user,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        String tenantId,
        String modelGroupId,
        String actionName,
        MLClient client,
        MLSdkClient sdkClient,
        ActionListener<Boolean> listener
    );
}
