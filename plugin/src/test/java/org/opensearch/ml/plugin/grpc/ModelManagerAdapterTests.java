/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.plugin.grpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.test.OpenSearchTestCase;

public class ModelManagerAdapterTests extends OpenSearchTestCase {

    @Mock
    private MLModelManager mockDelegate;

    @Mock
    private ActionListener<MLModel> mockListener;

    private ModelManagerAdapter adapter;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        adapter = new ModelManagerAdapter(mockDelegate);
    }

    public void testGetModel_delegatesWithModelIdAndTenantId() {
        adapter.getModel("model-1", "tenant-1", mockListener);

        verify(mockDelegate).getModel(eq("model-1"), eq("tenant-1"), same(mockListener));
    }

    public void testGetModel_withNullTenantId() {
        adapter.getModel("model-1", null, mockListener);

        verify(mockDelegate).getModel(eq("model-1"), eq(null), same(mockListener));
    }

    public void testGetOptionalModelFunctionName_returnsPresent() {
        when(mockDelegate.getOptionalModelFunctionName("model-1")).thenReturn(Optional.of(FunctionName.REMOTE));

        Optional<FunctionName> result = adapter.getOptionalModelFunctionName("model-1");

        assertTrue(result.isPresent());
        assertEquals(FunctionName.REMOTE, result.get());
    }

    public void testGetOptionalModelFunctionName_returnsEmpty() {
        when(mockDelegate.getOptionalModelFunctionName("model-1")).thenReturn(Optional.empty());

        Optional<FunctionName> result = adapter.getOptionalModelFunctionName("model-1");

        assertTrue(result.isEmpty());
    }
}
