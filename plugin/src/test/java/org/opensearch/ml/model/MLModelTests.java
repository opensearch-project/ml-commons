/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.ml.model;

import static org.junit.Assert.assertEquals;
import static org.opensearch.common.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;

import org.junit.Test;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.utils.TestHelper;

public class MLModelTests {

    @Test
    public void toXContent() throws IOException {
        MLModel mlModel = MLModel.builder().algorithm(FunctionName.KMEANS).name("model_name").version(1).content("test_content").build();
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mlModel.toXContent(builder, EMPTY_PARAMS);
        String mlModelContent = TestHelper.xContentBuilderToString(builder);
        assertEquals("{\"name\":\"model_name\",\"algorithm\":\"KMEANS\",\"version\":1,\"content\":\"test_content\"}", mlModelContent);
    }

    @Test
    public void toXContent_NullValue() throws IOException {
        MLModel mlModel = MLModel.builder().build();
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mlModel.toXContent(builder, EMPTY_PARAMS);
        String mlModelContent = TestHelper.xContentBuilderToString(builder);
        assertEquals("{}", mlModelContent);
    }
}
