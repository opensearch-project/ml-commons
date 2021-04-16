/*
 *  Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License").
 *  You may not use this file except in compliance with the License.
 *  A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package org.opensearch.ml.common.transport.training;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.junit.Test;
import org.opensearch.action.ActionResponse;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamOutput;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class MLTrainingTaskResponseTest {

    @Test
    public void writeTo() throws IOException {
        MLTrainingTaskResponse response = MLTrainingTaskResponse.builder()
                .status("success")
                .taskId("taskId")
                .build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        response.writeTo(bytesStreamOutput);
        assertEquals(15, bytesStreamOutput.size());
        response = new MLTrainingTaskResponse(bytesStreamOutput.bytes().streamInput());
        assertEquals("success", response.getStatus());
        assertEquals("taskId", response.getTaskId());
    }

    @Test
    public void fromActionResponse_Success_WithMLTrainingTaskResponse() {
        MLTrainingTaskResponse response = MLTrainingTaskResponse.builder()
                .status("success")
                .taskId("taskId")
                .build();
        assertSame(response, MLTrainingTaskResponse.fromActionResponse(response));
    }

    @Test
    public void fromActionResponse_Success_WithNonMLTrainingTaskResponse() {
        MLTrainingTaskResponse response = MLTrainingTaskResponse.builder()
                .status("success")
                .taskId("taskId")
                .build();
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                response.writeTo(out);
            }
        };

        MLTrainingTaskResponse result = MLTrainingTaskResponse.fromActionResponse(actionResponse);
        assertNotSame(response, result);
        assertEquals(response.getStatus(), result.getStatus());
        assertEquals(response.getTaskId(), result.getTaskId());
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionResponse_Exception() {
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException("test");
            }
        };

        MLTrainingTaskResponse.fromActionResponse(actionResponse);
    }
}