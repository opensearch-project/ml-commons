/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.profile;

import java.io.IOException;

import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.ml.profile.MLProfileInput;
import org.opensearch.test.OpenSearchTestCase;

public class MLProfileRequestTests extends OpenSearchTestCase {

    public void testSerializationDeserialization() throws IOException {
        MLProfileRequest mlTaskProfileRequest = new MLProfileRequest(new String[] { "testNodeId" }, new MLProfileInput());
        BytesStreamOutput output = new BytesStreamOutput();

        mlTaskProfileRequest.writeTo(output);
        MLProfileRequest request = new MLProfileRequest(output.bytes().streamInput());
        assertNotNull(mlTaskProfileRequest.getMlProfileInput());
        assertTrue(mlTaskProfileRequest.getMlProfileInput().getTaskIds().isEmpty());
        assertEquals(mlTaskProfileRequest.getMlProfileInput().getNodeIds().size(), 0);
    }
}
