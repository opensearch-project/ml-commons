/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.common.input.Constants;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.rest.RestRequest;
import org.opensearch.search.builder.SearchSourceBuilder;

public class TenantAwareHelper {

    /**
     * Validates the tenant ID based on the multi-tenancy feature setting.
     *
     * @param mlFeatureEnabledSetting The settings that indicate whether the multi-tenancy feature is enabled.
     * @param tenantId The tenant ID to validate.
     * @param listener The action listener to handle failure cases.
     * @return true if the tenant ID is valid or if multi-tenancy is not enabled; false if the tenant ID is invalid and multi-tenancy is enabled.
     */
    public static boolean validateTenantId(MLFeatureEnabledSetting mlFeatureEnabledSetting, String tenantId, ActionListener<?> listener) {
        if (mlFeatureEnabledSetting.isMultiTenancyEnabled() && tenantId == null) {
            listener.onFailure(new OpenSearchStatusException("You don't have permission to access this resource", RestStatus.FORBIDDEN));
            return false;
        } else
            return true;
    }

    /**
     * Validates the tenant resource by comparing the tenant ID from the request with the tenant ID from the resource.
     *
     * @param mlFeatureEnabledSetting The settings that indicate whether the multi-tenancy feature is enabled.
     * @param tenantIdFromRequest The tenant ID obtained from the request.
     * @param tenantIdFromResource The tenant ID obtained from the resource.
     * @param listener The action listener to handle failure cases.
     * @return true if the tenant IDs match or if multi-tenancy is not enabled; false if the tenant IDs do not match and multi-tenancy is enabled.
     */
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

    public static boolean isTenantFilteringEnabled(SearchRequest searchRequest) {
        SearchSourceBuilder searchSourceBuilder = searchRequest.source();
        if (searchSourceBuilder != null) {
            QueryBuilder queryBuilder = searchSourceBuilder.query();
            if (queryBuilder instanceof TermQueryBuilder) {
                TermQueryBuilder termQuery = (TermQueryBuilder) queryBuilder;
                return TENANT_ID_FIELD.equals(termQuery.fieldName()); // Tenant filtering is enabled
            }
        }
        return false; // Tenant filtering is not enabled
    }

    public static String getTenantID(Boolean isMultiTenancyEnabled, RestRequest restRequest) {
        if (!isMultiTenancyEnabled) {
            return null;
        }

        Map<String, List<String>> headers = restRequest.getHeaders();
        if (headers == null) {
            throw new OpenSearchStatusException("Rest request headers can't be null", RestStatus.FORBIDDEN);
        }

        List<String> tenantIdList = headers.get(Constants.TENANT_ID_HEADER);
        if (tenantIdList == null || tenantIdList.isEmpty()) {
            throw new OpenSearchStatusException("Tenant ID header is missing or has no value", RestStatus.FORBIDDEN);
        }

        String tenantId = tenantIdList.get(0);
        if (tenantId == null) {
            throw new OpenSearchStatusException("Tenant ID can't be null", RestStatus.FORBIDDEN);
        }

        return tenantId;
    }
}
