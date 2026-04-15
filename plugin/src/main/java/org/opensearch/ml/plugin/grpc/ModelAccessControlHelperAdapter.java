/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.plugin.grpc;

import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.grpc.interfaces.MLClient;
import org.opensearch.ml.grpc.interfaces.MLModelAccessControlHelper;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.transport.client.Client;

/**
 * Adapter that wraps ModelAccessControlHelper to implement the gRPC interface.
 * This breaks the circular dependency between grpc and plugin modules.
 */
public class ModelAccessControlHelperAdapter implements MLModelAccessControlHelper {

    private final ModelAccessControlHelper delegate;

    public ModelAccessControlHelperAdapter(ModelAccessControlHelper delegate) {
        this.delegate = delegate;
    }

    @Override
    public void validateModelGroupAccess(
        User user,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        String tenantId,
        String modelGroupId,
        String actionName,
        MLClient client,
        Object sdkClient,
        ActionListener<Boolean> listener
    ) {
        // Unwrap the real client and SDK client from the adapters
        Client realClient = ((ClientAdapter) client).getDelegate();
        SdkClient realSdkClient = (SdkClient) sdkClient;

        // Delegate to the real implementation
        delegate
            .validateModelGroupAccess(
                user,
                mlFeatureEnabledSetting,
                tenantId,
                modelGroupId,
                actionName,
                realClient,
                realSdkClient,
                listener
            );
    }
}
