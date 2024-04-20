/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.profile;

import java.io.IOException;
import java.util.Collections;

import org.junit.Test;
import org.opensearch.Version;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.profile.MLProfileInput;
import org.opensearch.test.OpenSearchTestCase;

public class MLProfileRequestTests extends OpenSearchTestCase {

    @Test
    public void testSerializationDeserialization() throws IOException {
        // Prepare the MLProfileRequest with a test version
        Version testVersion = Version.fromString("2.12.0"); // Adjust the version as needed
        MLProfileRequest mlProfileRequest = new MLProfileRequest(new String[] { "testNodeId" }, new MLProfileInput());
        mlProfileRequest.setHiddenModelIds(Collections.singleton("modelID"));

        BytesStreamOutput output = new BytesStreamOutput();
        output.setVersion(testVersion);

        mlProfileRequest.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        input.setVersion(testVersion);

        MLProfileRequest deserializedRequest = new MLProfileRequest(input);
        assertNotNull(deserializedRequest.getMlProfileInput());
        assertTrue(deserializedRequest.getMlProfileInput().getTaskIds().isEmpty());
        assertEquals(0, deserializedRequest.getMlProfileInput().getNodeIds().size());
        assertTrue(deserializedRequest.getHiddenModelIds().contains("modelID"));
    }

    @Test
    public void testSerializationDeserializationWithVersionBelowThreshold() throws IOException {
        Version oldVersion = Version.fromString("1.0.0"); // Example version before minimal supported
        MLProfileRequest mlProfileRequest = new MLProfileRequest(new String[] { "testNodeId" }, new MLProfileInput());
        mlProfileRequest.setHiddenModelIds(Collections.singleton("modelID"));

        BytesStreamOutput output = new BytesStreamOutput();
        output.setVersion(oldVersion);

        mlProfileRequest.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        input.setVersion(oldVersion);

        MLProfileRequest deserializedRequest = new MLProfileRequest(input);
        assertNotNull(deserializedRequest.getMlProfileInput());
        assertTrue(deserializedRequest.getMlProfileInput().getTaskIds().isEmpty());
        assertEquals(0, deserializedRequest.getMlProfileInput().getNodeIds().size());
        // Check if hiddenModelIds are not serialized and deserialized correctly below version threshold
        assertTrue(deserializedRequest.getHiddenModelIds().isEmpty());
    }

    @Test
    public void testSerializationDeserializationWithVersionAtOrAboveThreshold() throws IOException {
        Version newVersion = MLRegisterModelInput.MINIMAL_SUPPORTED_VERSION_FOR_AGENT_FRAMEWORK;
        MLProfileRequest mlProfileRequest = new MLProfileRequest(new String[] { "testNodeId" }, new MLProfileInput());
        mlProfileRequest.setHiddenModelIds(Collections.singleton("modelID"));

        BytesStreamOutput output = new BytesStreamOutput();
        output.setVersion(newVersion);

        mlProfileRequest.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        input.setVersion(newVersion);

        MLProfileRequest deserializedRequest = new MLProfileRequest(input);
        assertNotNull(deserializedRequest.getMlProfileInput());
        assertTrue(deserializedRequest.getMlProfileInput().getTaskIds().isEmpty());
        assertEquals(0, deserializedRequest.getMlProfileInput().getNodeIds().size());
        // Check if hiddenModelIds are serialized and deserialized correctly at or above version threshold
        assertTrue(deserializedRequest.getHiddenModelIds().contains("modelID"));
    }
}
