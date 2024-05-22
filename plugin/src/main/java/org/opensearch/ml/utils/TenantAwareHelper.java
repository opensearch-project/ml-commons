package org.opensearch.ml.utils;

import java.util.Objects;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;

import lombok.Getter;
import lombok.Setter;

public class TenantAwareHelper implements TenantAware {

    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    @Getter
    @Setter
    private String tenantId;

    public TenantAwareHelper(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getTenantId() {
        return tenantId;
    }

    @Override
    public boolean validateTenantId(ActionListener<?> listener) {
        if (mlFeatureEnabledSetting.isMultiTenancyEnabled() && getTenantId() == null) {
            listener.onFailure(new OpenSearchStatusException("You don't have permission to access this resource", RestStatus.FORBIDDEN));
            return false;
        } else
            return true;
    }

    @Override
    public boolean validateTenantResource(String tenantIdFromResource, ActionListener<?> listener) {
        if (mlFeatureEnabledSetting.isMultiTenancyEnabled() && !Objects.equals(tenantId, tenantIdFromResource)) {
            listener.onFailure(new OpenSearchStatusException("You don't have permission to access this resource", RestStatus.FORBIDDEN));
            return false;
        } else
            return true;
    }
}
