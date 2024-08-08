/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Test;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;

public class MLTrainingOutputTest {

    @Test
    public void parse_MLTrain() throws IOException {
        MLTrainingOutput output = MLTrainingOutput.builder().modelId("test_modelId").status("test_status").build();
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        output.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();
        assertEquals("{\"model_id\":\"test_modelId\",\"status\":\"test_status\"}", jsonStr);
    }
}
