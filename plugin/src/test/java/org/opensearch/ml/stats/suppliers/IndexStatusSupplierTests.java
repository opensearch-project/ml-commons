/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats.suppliers;

import static org.mockito.Mockito.when;
import static org.opensearch.ml.stats.suppliers.IndexStatusSupplier.UNABLE_TO_RETRIEVE_HEALTH_MESSAGE;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ml.utils.IndexUtils;
import org.opensearch.test.OpenSearchTestCase;

public class IndexStatusSupplierTests extends OpenSearchTestCase {

    @Mock
    IndexUtils indexUtils;
    IndexStatusSupplier supplier;
    String indexName = "test_index";
    String indexStatus = "green";

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        supplier = new IndexStatusSupplier(indexUtils, indexName);
    }

    public void testGet() {
        when(indexUtils.getIndexHealthStatus(indexName)).thenReturn(indexStatus);
        assertEquals(indexStatus, supplier.get());
    }

    public void testGet_Exception() {
        when(indexUtils.getIndexHealthStatus(indexName)).thenThrow(new RuntimeException("test exception"));
        assertEquals(UNABLE_TO_RETRIEVE_HEALTH_MESSAGE, supplier.get());
    }
}
