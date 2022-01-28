/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.parameter;

import org.junit.Test;
import org.opensearch.common.Strings;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import java.io.IOException;
import static org.junit.Assert.assertEquals;

public class MLTrainingOutputTest {

    @Test
    public void parse_MLTrain() throws IOException {
        MLTrainingOutput output = MLTrainingOutput.builder()
                .modelId("test_modelId").status("test_status").build();
        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        output.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = Strings.toString(builder);
        assertEquals("{\"model_id\":\"test_modelId\",\"status\":\"test_status\"}", jsonStr);
    }
}
