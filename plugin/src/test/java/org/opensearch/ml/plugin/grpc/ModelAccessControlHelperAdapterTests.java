/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.plugin.grpc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.client.Client;

public class ModelAccessControlHelperAdapterTests extends OpenSearchTestCase {

    @Mock
    private ModelAccessControlHelper mockDelegate;

    @Mock
    private Client mockClient;

    @Mock
    private SdkClient mockSdkClient;

    @Mock
    private MLFeatureEnabledSetting mockFeatureSettings;

    @Mock
    private ActionListener<Boolean> mockListener;

    private User user;
    private ModelAccessControlHelperAdapter adapter;
    private ClientAdapter clientAdapter;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        user = new User();
        adapter = new ModelAccessControlHelperAdapter(mockDelegate);
        clientAdapter = new ClientAdapter(mockClient);
    }

    public void testValidateModelGroupAccess_delegatesToRealHelper() {
        adapter
            .validateModelGroupAccess(
                user,
                mockFeatureSettings,
                "tenant-1",
                "group-1",
                "action-name",
                clientAdapter,
                mockSdkClient,
                mockListener
            );

        verify(mockDelegate)
            .validateModelGroupAccess(
                same(user),
                same(mockFeatureSettings),
                eq("tenant-1"),
                eq("group-1"),
                eq("action-name"),
                same(mockClient),
                same(mockSdkClient),
                same(mockListener)
            );
    }

    public void testValidateModelGroupAccess_withNullTenantId() {
        adapter
            .validateModelGroupAccess(
                user,
                mockFeatureSettings,
                null,
                "group-1",
                "action-name",
                clientAdapter,
                mockSdkClient,
                mockListener
            );

        verify(mockDelegate)
            .validateModelGroupAccess(
                same(user),
                same(mockFeatureSettings),
                eq(null),
                eq("group-1"),
                eq("action-name"),
                same(mockClient),
                any(SdkClient.class),
                same(mockListener)
            );
    }

    public void testValidateModelGroupAccess_unwrapsClientAdapter() {
        adapter
            .validateModelGroupAccess(
                user,
                mockFeatureSettings,
                "tenant-1",
                "group-1",
                "action-name",
                clientAdapter,
                mockSdkClient,
                mockListener
            );

        verify(mockDelegate).validateModelGroupAccess(any(), any(), any(), any(), any(), same(mockClient), any(), any());
    }
}
