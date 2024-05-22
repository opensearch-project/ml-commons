package org.opensearch.ml.utils;

import org.opensearch.core.action.ActionListener;

public interface TenantAware {
    String getTenantId();

    boolean validateTenantId(ActionListener<?> listener);

    boolean validateTenantResource(String tenantIdFromResource, ActionListener<?> listener);
}
