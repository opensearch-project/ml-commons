/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import java.util.Objects;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;

public class TenantAwareHelper {

    public static boolean validateTenantId(MLFeatureEnabledSetting mlFeatureEnabledSetting, String tenantId, ActionListener<?> listener) {
        if (mlFeatureEnabledSetting.isMultiTenancyEnabled() && tenantId == null) {
            listener.onFailure(new OpenSearchStatusException("You don't have permission to access this resource", RestStatus.FORBIDDEN));
            return false;
        } else
            return true;
    }

    public static boolean validateTenantResource(
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        String tenantIdFromRequest,
        String tenantIdFromResource,
        ActionListener<?> listener
    ) {
        if (mlFeatureEnabledSetting.isMultiTenancyEnabled() && !Objects.equals(tenantIdFromRequest, tenantIdFromResource)) {
            listener.onFailure(new OpenSearchStatusException("You don't have permission to access this resource", RestStatus.FORBIDDEN));
            return false;
        } else
            return true;
    }
}
