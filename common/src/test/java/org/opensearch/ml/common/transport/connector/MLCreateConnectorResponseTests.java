/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.TestHelper;

public class MLCreateConnectorResponseTests {

    @Test
    public void toXContent() throws IOException {
        MLCreateConnectorResponse response = new MLCreateConnectorResponse("test_id");
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);
        Assert.assertEquals("{\"connector_id\":\"test_id\"}", content);
    }

    @Test
    public void readFromStream() throws IOException {
        MLCreateConnectorResponse response = new MLCreateConnectorResponse("test_id");
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);

        MLCreateConnectorResponse response2 = new MLCreateConnectorResponse(output.bytes().streamInput());
        Assert.assertEquals("test_id", response2.getConnectorId());
    }
}
