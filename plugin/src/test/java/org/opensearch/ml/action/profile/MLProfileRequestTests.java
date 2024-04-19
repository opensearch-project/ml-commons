/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.profile;

import java.io.IOException;
import java.util.Collections;

import org.junit.Assert;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.ml.profile.MLProfileInput;
import org.opensearch.test.OpenSearchTestCase;

public class MLProfileRequestTests extends OpenSearchTestCase {

    public void testSerializationDeserialization() throws IOException {
        MLProfileRequest mlTaskProfileRequest = new MLProfileRequest(new String[] { "testNodeId" }, new MLProfileInput());
        mlTaskProfileRequest.setHiddenModelIds(Collections.singleton("modelID"));
        BytesStreamOutput output = new BytesStreamOutput();

        mlTaskProfileRequest.writeTo(output);
        MLProfileRequest request = new MLProfileRequest(output.bytes().streamInput());
        assertNotNull(request.getMlProfileInput());
        assertTrue(request.getMlProfileInput().getTaskIds().isEmpty());
        assertEquals(request.getMlProfileInput().getNodeIds().size(), 0);
        Assert.assertTrue(request.getHiddenModelIds().contains("modelID"));
    }
}
