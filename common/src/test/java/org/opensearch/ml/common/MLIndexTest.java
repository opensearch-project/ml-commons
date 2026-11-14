/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import static org.junit.Assert.assertEquals;
import static org.opensearch.ml.common.CommonValue.ML_CONFIG_INDEX_MAPPING_PATH;
import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX_MAPPING_PATH;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX_MAPPING_PATH;

import java.io.IOException;
import java.util.Map;

import org.junit.Test;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.XContentType;
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

    @Test
    @SuppressWarnings("unchecked")
    public void testAuthConfigMappings() {
        assertEquals(Integer.valueOf(6), MLIndex.CONNECTOR.getVersion());
        assertEquals(Integer.valueOf(15), MLIndex.MODEL.getVersion());

        Map<String, Object> connectorProperties = (Map<String, Object>) XContentHelper
            .convertToMap(XContentType.JSON.xContent(), MLIndex.CONNECTOR.getMapping(), true)
            .get("properties");
        assertEquals(Map.of("type", "flat_object"), connectorProperties.get("auth_config"));

        // The model mapping substitutes the connector placeholder, so the inline connector gets the field too.
        Map<String, Object> modelProperties = (Map<String, Object>) XContentHelper
            .convertToMap(XContentType.JSON.xContent(), MLIndex.MODEL.getMapping(), true)
            .get("properties");
        Map<String, Object> inlineConnector = (Map<String, Object>) modelProperties.get("connector");
        Map<String, Object> inlineProperties = (Map<String, Object>) inlineConnector.get("properties");
        assertEquals(Map.of("type", "flat_object"), inlineProperties.get("auth_config"));
        assertEquals(Map.of("type", "flat_object"), inlineProperties.get("parameters"));
    }
}
