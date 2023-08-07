/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output;

import org.junit.Test;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;
import static org.junit.Assert.assertEquals;

public class MLTrainingOutputTest {

    @Test
    public void parse_MLTrain() throws IOException {
        MLTrainingOutput output = MLTrainingOutput.builder()
                .modelId("test_modelId").status("test_status").build();
        XContentBuilder builder = XContentFactory.jsonBuilder();
        output.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = StringUtils.xContentBuilderToString(builder);
        assertEquals("{\"model_id\":\"test_modelId\",\"status\":\"test_status\"}", jsonStr);
    }
}
