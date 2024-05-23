/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;

public class TenantAwareHelperTests {

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private ActionListener<?> actionListener;

    private TenantAwareHelper tenantAwareHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testValidateTenantId_MultiTenancyEnabled_TenantIdNull() {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        boolean result = tenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, null, actionListener);
        assertFalse(result);
        ArgumentCaptor<OpenSearchStatusException> captor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(captor.capture());
        OpenSearchStatusException exception = captor.getValue();
        assert exception.status() == RestStatus.FORBIDDEN;
        assert exception.getMessage().equals("You don't have permission to access this resource");
    }

    @Test
    public void testValidateTenantId_MultiTenancyEnabled_TenantIdPresent() {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        boolean result = tenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, "tenant_id", actionListener);
        assertTrue(result);
    }

    @Test
    public void testValidateTenantId_MultiTenancyDisabled() {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        boolean result = tenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, null, actionListener);

        assertTrue(result);
    }

    @Test
    public void testValidateTenantResource_MultiTenancyEnabled_TenantIdMismatch() {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        boolean result = tenantAwareHelper.validateTenantResource(mlFeatureEnabledSetting, null, "different_tenant_id", actionListener);
        assertFalse(result);
        ArgumentCaptor<OpenSearchStatusException> captor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(captor.capture());
        OpenSearchStatusException exception = captor.getValue();
        assert exception.status() == RestStatus.FORBIDDEN;
        assert exception.getMessage().equals("You don't have permission to access this resource");
    }

    @Test
    public void testValidateTenantResource_MultiTenancyEnabled_TenantIdMatch() {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        boolean result = tenantAwareHelper.validateTenantResource(mlFeatureEnabledSetting, "tenant_id", "tenant_id", actionListener);
        assertTrue(result);
    }

    @Test
    public void testValidateTenantResource_MultiTenancyDisabled() {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        boolean result = tenantAwareHelper.validateTenantResource(mlFeatureEnabledSetting, "tenant_id", "different_tenant_id", actionListener);
        assertTrue(result);
    }
}
