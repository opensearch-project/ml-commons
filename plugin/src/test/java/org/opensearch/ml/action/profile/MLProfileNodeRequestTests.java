/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.profile;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.ml.profile.MLProfileInput;
import org.opensearch.test.OpenSearchTestCase;

public class MLProfileNodeRequestTests extends OpenSearchTestCase {

    public void testConstructorSerialization() throws IOException {
        Set<String> taskIds = Stream.of("id1", "id2", "id3").collect(Collectors.toCollection(HashSet::new));
        MLProfileInput mlProfileInput = new MLProfileInput(new HashSet<>(), taskIds, new HashSet<>(), false, false, null);
        MLProfileNodeRequest mlProfileNodeRequest = new MLProfileNodeRequest(
            new MLProfileRequest(new String[] { "testNodeId" }, mlProfileInput)
        );
        BytesStreamOutput output = new BytesStreamOutput();

        mlProfileNodeRequest.writeTo(output);
        MLProfileNodeRequest request = new MLProfileNodeRequest(output.bytes().streamInput());

        assertNotNull(mlProfileNodeRequest.getMlProfileRequest().getMlProfileInput());
        assertEquals(mlProfileNodeRequest.getMlProfileRequest().getMlProfileInput().getTaskIds().size(), 3);
    }
}
