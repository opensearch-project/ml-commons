/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MULTI_TENANCY_ENABLED;
import static org.opensearch.ml.utils.TestHelper.xContentRegistry;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.common.input.Constants;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.rest.RestRequest;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.rest.FakeRestRequest;

public class TenantAwareHelperTests {

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private ActionListener<?> actionListener;

    Settings settings = Settings.builder().put(ML_COMMONS_MULTI_TENANCY_ENABLED.getKey(), true).build();

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testValidateTenantId_MultiTenancyEnabled_TenantIdNull() {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        boolean result = TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, null, actionListener);
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
        boolean result = TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, "_tenant_id", actionListener);
        assertTrue(result);
    }

    @Test
    public void testValidateTenantId_MultiTenancyDisabled() {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        boolean result = TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, null, actionListener);

        assertTrue(result);
    }

    @Test
    public void testValidateTenantResource_MultiTenancyEnabled_TenantIdMismatch() {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        boolean result = TenantAwareHelper.validateTenantResource(mlFeatureEnabledSetting, null, "different_tenant_id", actionListener);
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
        boolean result = TenantAwareHelper.validateTenantResource(mlFeatureEnabledSetting, "_tenant_id", "_tenant_id", actionListener);
        assertTrue(result);
    }

    @Test
    public void testValidateTenantResource_MultiTenancyDisabled() {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        boolean result = TenantAwareHelper.validateTenantResource(mlFeatureEnabledSetting, "_tenant_id", "different_tenant_id", actionListener);
        assertTrue(result);
    }

    @Test
    public void testIsTenantFilteringEnabled_TenantFilteringEnabled() {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(new TermQueryBuilder(TENANT_ID_FIELD, "123456"));
        SearchRequest searchRequest = new SearchRequest().source(sourceBuilder);

        boolean result = TenantAwareHelper.isTenantFilteringEnabled(searchRequest);
        assertTrue(result);
    }

    @Test
    public void testIsTenantFilteringEnabled_TenantFilteringDisabled() {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.matchAllQuery());
        SearchRequest searchRequest = new SearchRequest().source(sourceBuilder);

        boolean result = TenantAwareHelper.isTenantFilteringEnabled(searchRequest);
        assertFalse(result);
    }

    @Test
    public void testIsTenantFilteringEnabled_NoQuery() {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        SearchRequest searchRequest = new SearchRequest().source(sourceBuilder);

        boolean result = TenantAwareHelper.isTenantFilteringEnabled(searchRequest);
        assertFalse(result);
    }

    @Test
    public void testIsTenantFilteringEnabled_NullSource() {
        SearchRequest searchRequest = new SearchRequest();

        boolean result = TenantAwareHelper.isTenantFilteringEnabled(searchRequest);
        assertFalse(result);
    }

    @Test
    public void testGetTenantID_IndependentNode() {
        String tenantId = "test-tenant";
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(Constants.TENANT_ID_HEADER, Collections.singletonList(tenantId));
        RestRequest restRequest = new FakeRestRequest.Builder(xContentRegistry()).withHeaders(headers).build();

        String actualTenantID = TenantAwareHelper.getTenantID(Boolean.TRUE, restRequest);
        Assert.assertEquals(tenantId, actualTenantID);
    }

    @Test
    public void testGetTenantID_IndependentNode_NullTenantID() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(Constants.TENANT_ID_HEADER, Collections.singletonList(null));
        RestRequest restRequest = new FakeRestRequest.Builder(xContentRegistry()).withHeaders(headers).build();

        try {
            TenantAwareHelper.getTenantID(Boolean.TRUE, restRequest);
            Assert.fail("Expected OpenSearchStatusException");
        } catch (Exception e) {
            Assert.assertTrue(e instanceof OpenSearchStatusException);
            Assert.assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) e).status());
            Assert.assertEquals("Tenant ID can't be null", e.getMessage());
        }
    }

    @Test
    public void testGetTenantID_NotIndependentNode() {
        settings = Settings.builder().put(ML_COMMONS_MULTI_TENANCY_ENABLED.getKey(), false).build();
        String tenantId = "test-tenant";
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(Constants.TENANT_ID_HEADER, Collections.singletonList(tenantId));
        RestRequest restRequest = new FakeRestRequest.Builder(xContentRegistry()).withHeaders(headers).build();

        String tenantID = TenantAwareHelper.getTenantID(Boolean.FALSE, restRequest);
        Assert.assertNull(tenantID);
    }
}
