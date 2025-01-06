/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import static org.opensearch.ml.common.CommonValue.ML_CONFIG_INDEX_MAPPING_PATH;
import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX_MAPPING_PATH;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX_MAPPING_PATH;

import java.io.IOException;

import org.junit.Test;
import org.opensearch.ml.common.utils.IndexUtils;

public class MLIndexTest {

    /**
     * Mappings are initialised during runtime when the MLIndex enum is referenced.
     * We want to catch any failure in mapping assignment before runtime.
     * This test simply references the enums to fetch the mapping. It will fail in case the enum is not initialized.
    **/
    @Test
    public void testValidateMappingsForSystemIndices() {
        for (MLIndex index : MLIndex.values()) {
            String mapping = index.getMapping();
        }
    }

    // adding an explicit check for critical indices
    @Test
    public void testMappings() throws IOException {
        String model_mapping = IndexUtils.getMappingFromFile(ML_MODEL_INDEX_MAPPING_PATH);
        IndexUtils.validateMapping(model_mapping);

        String connector_mapping = IndexUtils.getMappingFromFile(ML_CONNECTOR_INDEX_MAPPING_PATH);
        IndexUtils.validateMapping(connector_mapping);

        String config_mapping = IndexUtils.getMappingFromFile(ML_CONFIG_INDEX_MAPPING_PATH);
        IndexUtils.validateMapping(config_mapping);
    }
}
