/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.common.transport.upload;

import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamOutput;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class UploadTaskRequestTest {

    private final String bodyExample = "PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiIHN0YW5kYWxvbmU9InllcyI/Pgo8UE1NTCB4bWxucz0iaHR0cDovL3d3dy5kbWcub3JnL1BNTUwtNF80IiB4bWxuczpkYXRhPSJodHRwOi8vanBtbWwub3JnL2pwbW1sLW1vZGVsL0lubGluZVRhYmxlIiB2ZXJzaW9uPSI0LjQiPgoJPEhlYWRlcj4KCQk8QXBwbGljYXRpb24gbmFtZT0iSlBNTUwtU2tMZWFybiIgdmVyc2lvbj0iMS42LjE4Ii8+CgkJPFRpbWVzdGFtcD4yMDIxLTA3LTE5VDIzOjIwOjI4WjwvVGltZXN0YW1wPgoJPC9IZWFkZXI+Cgk8RGF0YURpY3Rpb25hcnk+CgkJPERhdGFGaWVsZCBuYW1lPSJ4MSIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZmxvYXQiLz4KCTwvRGF0YURpY3Rpb25hcnk+Cgk8VHJhbnNmb3JtYXRpb25EaWN0aW9uYXJ5Lz4KCTxNaW5pbmdNb2RlbCBmdW5jdGlvbk5hbWU9InJlZ3Jlc3Npb24iIGFsZ29yaXRobU5hbWU9InNrbGVhcm4uZW5zZW1ibGUuX2lmb3Jlc3QuSXNvbGF0aW9uRm9yZXN0Ij4KCQk8TWluaW5nU2NoZW1hPgoJCQk8TWluaW5nRmllbGQgbmFtZT0ieDEiLz4KCQk8L01pbmluZ1NjaGVtYT4KCQk8T3V0cHV0PgoJCQk8T3V0cHV0RmllbGQgbmFtZT0icmF3QW5vbWFseVNjb3JlIiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiIGlzRmluYWxSZXN1bHQ9ImZhbHNlIi8+CgkJCTxPdXRwdXRGaWVsZCBuYW1lPSJub3JtYWxpemVkQW5vbWFseVNjb3JlIiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiIGZlYXR1cmU9InRyYW5zZm9ybWVkVmFsdWUiIGlzRmluYWxSZXN1bHQ9ImZhbHNlIj4KCQkJCTxBcHBseSBmdW5jdGlvbj0iLyI+CgkJCQkJPEZpZWxkUmVmIGZpZWxkPSJyYXdBbm9tYWx5U2NvcmUiLz4KCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+Mi4zMjcwMjAwNTIwNDI4NDc8L0NvbnN0YW50PgoJCQkJPC9BcHBseT4KCQkJPC9PdXRwdXRGaWVsZD4KCQkJPE91dHB1dEZpZWxkIG5hbWU9ImRlY2lzaW9uRnVuY3Rpb24iIG9wdHlwZT0iY29udGludW91cyIgZGF0YVR5cGU9ImRvdWJsZSIgZmVhdHVyZT0idHJhbnNmb3JtZWRWYWx1ZSI+CgkJCQk8QXBwbHkgZnVuY3Rpb249Ii0iPgoJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4wLjU8L0NvbnN0YW50PgoJCQkJCTxBcHBseSBmdW5jdGlvbj0icG93Ij4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjIuMDwvQ29uc3RhbnQ+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iKiI+CgkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+LTEuMDwvQ29uc3RhbnQ+CgkJCQkJCQk8RmllbGRSZWYgZmllbGQ9Im5vcm1hbGl6ZWRBbm9tYWx5U2NvcmUiLz4KCQkJCQkJPC9BcHBseT4KCQkJCQk8L0FwcGx5PgoJCQkJPC9BcHBseT4KCQkJPC9PdXRwdXRGaWVsZD4KCQkJPE91dHB1dEZpZWxkIG5hbWU9Im91dGxpZXIiIG9wdHlwZT0iY2F0ZWdvcmljYWwiIGRhdGFUeXBlPSJib29sZWFuIiBmZWF0dXJlPSJ0cmFuc2Zvcm1lZFZhbHVlIj4KCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc09yRXF1YWwiPgoJCQkJCTxGaWVsZFJlZiBmaWVsZD0iZGVjaXNpb25GdW5jdGlvbiIvPgoJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4wLjA8L0NvbnN0YW50PgoJCQkJPC9BcHBseT4KCQkJPC9PdXRwdXRGaWVsZD4KCQk8L091dHB1dD4KCQk8TG9jYWxUcmFuc2Zvcm1hdGlvbnM+CgkJCTxEZXJpdmVkRmllbGQgbmFtZT0iZG91YmxlKHgxKSIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIj4KCQkJCTxGaWVsZFJlZiBmaWVsZD0ieDEiLz4KCQkJPC9EZXJpdmVkRmllbGQ+CgkJPC9Mb2NhbFRyYW5zZm9ybWF0aW9ucz4KCQk8U2VnbWVudGF0aW9uIG11bHRpcGxlTW9kZWxNZXRob2Q9ImF2ZXJhZ2UiPgoJCQk8U2VnbWVudCBpZD0iMSI+CgkJCQk8VHJ1ZS8+CgkJCQk8VHJlZU1vZGVsIGZ1bmN0aW9uTmFtZT0icmVncmVzc2lvbiIgbWlzc2luZ1ZhbHVlU3RyYXRlZ3k9Im51bGxQcmVkaWN0aW9uIiBub1RydWVDaGlsZFN0cmF0ZWd5PSJyZXR1cm5MYXN0UHJlZGljdGlvbiI+CgkJCQkJPE1pbmluZ1NjaGVtYT4KCQkJCQkJPE1pbmluZ0ZpZWxkIG5hbWU9ImRvdWJsZSh4MSkiLz4KCQkJCQk8L01pbmluZ1NjaGVtYT4KCQkJCQk8Tm9kZSBzY29yZT0iMS4wIj4KCQkJCQkJPFRydWUvPgoJCQkJCQk8Tm9kZSBzY29yZT0iMy4wIj4KCQkJCQkJCTxTaW1wbGVQcmVkaWNhdGUgZmllbGQ9ImRvdWJsZSh4MSkiIG9wZXJhdG9yPSJsZXNzT3JFcXVhbCIgdmFsdWU9IjIuMjU2NTYwNzQyNTA4ODgxNCIvPgoJCQkJCQkJPE5vZGUgc2NvcmU9IjMuMCI+CgkJCQkJCQkJPFNpbXBsZVByZWRpY2F0ZSBmaWVsZD0iZG91YmxlKHgxKSIgb3BlcmF0b3I9Imxlc3NPckVxdWFsIiB2YWx1ZT0iMS45NjkzMTc0NzU3ODUxODM1Ii8+CgkJCQkJCQk8L05vZGU+CgkJCQkJCTwvTm9kZT4KCQkJCQk8L05vZGU+CgkJCQk8L1RyZWVNb2RlbD4KCQkJPC9TZWdtZW50PgoJCQk8U2VnbWVudCBpZD0iMiI+CgkJCQk8VHJ1ZS8+CgkJCQk8VHJlZU1vZGVsIGZ1bmN0aW9uTmFtZT0icmVncmVzc2lvbiIgbWlzc2luZ1ZhbHVlU3RyYXRlZ3k9Im51bGxQcmVkaWN0aW9uIiBub1RydWVDaGlsZFN0cmF0ZWd5PSJyZXR1cm5MYXN0UHJlZGljdGlvbiI+CgkJCQkJPE1pbmluZ1NjaGVtYT4KCQkJCQkJPE1pbmluZ0ZpZWxkIG5hbWU9ImRvdWJsZSh4MSkiLz4KCQkJCQk8L01pbmluZ1NjaGVtYT4KCQkJCQk8Tm9kZSBzY29yZT0iMi4wIj4KCQkJCQkJPFRydWUvPgoJCQkJCQk8Tm9kZSBzY29yZT0iMi4wIj4KCQkJCQkJCTxTaW1wbGVQcmVkaWNhdGUgZmllbGQ9ImRvdWJsZSh4MSkiIG9wZXJhdG9yPSJsZXNzT3JFcXVhbCIgdmFsdWU9IjEuNDQ0MzAyMjE5MTczMDgwMyIvPgoJCQkJCQk8L05vZGU+CgkJCQkJCTxOb2RlIHNjb3JlPSIzLjAiPgoJCQkJCQkJPFNpbXBsZVByZWRpY2F0ZSBmaWVsZD0iZG91YmxlKHgxKSIgb3BlcmF0b3I9Imxlc3NPckVxdWFsIiB2YWx1ZT0iMi45OTIxMDc2NDkzMzAwOTk4Ii8+CgkJCQkJCTwvTm9kZT4KCQkJCQk8L05vZGU+CgkJCQk8L1RyZWVNb2RlbD4KCQkJPC9TZWdtZW50PgoJCQk8U2VnbWVudCBpZD0iMyI+CgkJCQk8VHJ1ZS8+CgkJCQk8VHJlZU1vZGVsIGZ1bmN0aW9uTmFtZT0icmVncmVzc2lvbiIgbWlzc2luZ1ZhbHVlU3RyYXRlZ3k9Im51bGxQcmVkaWN0aW9uIiBub1RydWVDaGlsZFN0cmF0ZWd5PSJyZXR1cm5MYXN0UHJlZGljdGlvbiI+CgkJCQkJPE1pbmluZ1NjaGVtYT4KCQkJCQkJPE1pbmluZ0ZpZWxkIG5hbWU9ImRvdWJsZSh4MSkiLz4KCQkJCQk8L01pbmluZ1NjaGVtYT4KCQkJCQk8Tm9kZSBzY29yZT0iMi4wIj4KCQkJCQkJPFRydWUvPgoJCQkJCQk8Tm9kZSBzY29yZT0iMi4wIj4KCQkJCQkJCTxTaW1wbGVQcmVkaWNhdGUgZmllbGQ9ImRvdWJsZSh4MSkiIG9wZXJhdG9yPSJsZXNzT3JFcXVhbCIgdmFsdWU9IjEuNDAxMTYyMDIxOTgwMjMwOCIvPgoJCQkJCQk8L05vZGU+CgkJCQkJCTxOb2RlIHNjb3JlPSIzLjAiPgoJCQkJCQkJPFNpbXBsZVByZWRpY2F0ZSBmaWVsZD0iZG91YmxlKHgxKSIgb3BlcmF0b3I9Imxlc3NPckVxdWFsIiB2YWx1ZT0iMi42MDkwNDIzOTA5OTg5MzgiLz4KCQkJCQkJPC9Ob2RlPgoJCQkJCTwvTm9kZT4KCQkJCTwvVHJlZU1vZGVsPgoJCQk8L1NlZ21lbnQ+CgkJCTxTZWdtZW50IGlkPSI0Ij4KCQkJCTxUcnVlLz4KCQkJCTxUcmVlTW9kZWwgZnVuY3Rpb25OYW1lPSJyZWdyZXNzaW9uIiBtaXNzaW5nVmFsdWVTdHJhdGVneT0ibnVsbFByZWRpY3Rpb24iIG5vVHJ1ZUNoaWxkU3RyYXRlZ3k9InJldHVybkxhc3RQcmVkaWN0aW9uIj4KCQkJCQk8TWluaW5nU2NoZW1hPgoJCQkJCQk8TWluaW5nRmllbGQgbmFtZT0iZG91YmxlKHgxKSIvPgoJCQkJCTwvTWluaW5nU2NoZW1hPgoJCQkJCTxOb2RlIHNjb3JlPSIyLjAiPgoJCQkJCQk8VHJ1ZS8+CgkJCQkJCTxOb2RlIHNjb3JlPSIyLjAiPgoJCQkJCQkJPFNpbXBsZVByZWRpY2F0ZSBmaWVsZD0iZG91YmxlKHgxKSIgb3BlcmF0b3I9Imxlc3NPckVxdWFsIiB2YWx1ZT0iMS4xNTA1NjkxNTQwMTk3MzI1Ii8+CgkJCQkJCTwvTm9kZT4KCQkJCQkJPE5vZGUgc2NvcmU9IjMuMCI+CgkJCQkJCQk8U2ltcGxlUHJlZGljYXRlIGZpZWxkPSJkb3VibGUoeDEpIiBvcGVyYXRvcj0ibGVzc09yRXF1YWwiIHZhbHVlPSIyLjc2OTA0MjIzOTg4MjUzNyIvPgoJCQkJCQk8L05vZGU+CgkJCQkJPC9Ob2RlPgoJCQkJPC9UcmVlTW9kZWw+CgkJCTwvU2VnbWVudD4KCQkJPFNlZ21lbnQgaWQ9IjUiPgoJCQkJPFRydWUvPgoJCQkJPFRyZWVNb2RlbCBmdW5jdGlvbk5hbWU9InJlZ3Jlc3Npb24iIG1pc3NpbmdWYWx1ZVN0cmF0ZWd5PSJudWxsUHJlZGljdGlvbiIgbm9UcnVlQ2hpbGRTdHJhdGVneT0icmV0dXJuTGFzdFByZWRpY3Rpb24iPgoJCQkJCTxNaW5pbmdTY2hlbWE+CgkJCQkJCTxNaW5pbmdGaWVsZCBuYW1lPSJkb3VibGUoeDEpIi8+CgkJCQkJPC9NaW5pbmdTY2hlbWE+CgkJCQkJPE5vZGUgc2NvcmU9IjEuMCI+CgkJCQkJCTxUcnVlLz4KCQkJCQkJPE5vZGUgc2NvcmU9IjMuMCI+CgkJCQkJCQk8U2ltcGxlUHJlZGljYXRlIGZpZWxkPSJkb3VibGUoeDEpIiBvcGVyYXRvcj0ibGVzc09yRXF1YWwiIHZhbHVlPSIyLjIwNjI1NDk5NTA1ODQwOTQiLz4KCQkJCQkJCTxOb2RlIHNjb3JlPSIzLjAiPgoJCQkJCQkJCTxTaW1wbGVQcmVkaWNhdGUgZmllbGQ9ImRvdWJsZSh4MSkiIG9wZXJhdG9yPSJsZXNzT3JFcXVhbCIgdmFsdWU9IjEuMjM4ODg0MTM3MTIzMzAzNSIvPgoJCQkJCQkJPC9Ob2RlPgoJCQkJCQk8L05vZGU+CgkJCQkJPC9Ob2RlPgoJCQkJPC9UcmVlTW9kZWw+CgkJCTwvU2VnbWVudD4KCQk8L1NlZ21lbnRhdGlvbj4KCTwvTWluaW5nTW9kZWw+CjwvUE1NTD4K";

    @Test
    public void writeTo_Success() throws IOException {
        UploadTaskRequest request = UploadTaskRequest.builder()
            .name("test")
            .format("pmml")
            .algorithm("isolationforest")
            .body(bodyExample)
            .build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        request.writeTo(bytesStreamOutput);
        assertEquals(6545, bytesStreamOutput.size());
        request = new UploadTaskRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals("test", request.getName());
        assertEquals("pmml", request.getFormat());
        assertEquals("isolationforest", request.getAlgorithm());
        assertEquals(bodyExample, request.getBody());
    }

    @Test
    public void validate_Success() {
        UploadTaskRequest request = UploadTaskRequest.builder()
            .name("test")
            .format("pmml")
            .algorithm("isolationforest")
            .body(bodyExample)
            .build();

        assertNull(request.validate());
    }

    @Test
    public void validate_Exception_NullName() {
        UploadTaskRequest request = UploadTaskRequest.builder()
            .name(null)
            .format("pmml")
            .algorithm("isolationforest")
            .body(bodyExample)
            .build();

        ActionRequestValidationException exception = request.validate();
        assertEquals("Validation Failed: 1: model name can't be null or empty;", exception.getMessage());
    }

    @Test
    public void validate_Exception_NullFormat() {
        UploadTaskRequest request = UploadTaskRequest.builder()
            .name("test")
            .format(null)
            .algorithm("isolationforest")
            .body(bodyExample)
            .build();

        ActionRequestValidationException exception = request.validate();
        assertEquals("Validation Failed: 1: model format can't be null or empty;", exception.getMessage());
    }

    @Test
    public void validate_Exception_InvalidFormat() {
        UploadTaskRequest request = UploadTaskRequest.builder()
            .name("test")
            .format("pickle")
            .algorithm("isolationforest")
            .body(bodyExample)
            .build();

        ActionRequestValidationException exception = request.validate();
        assertEquals("Validation Failed: 1: only pmml models are supported in upload now;", exception.getMessage());
    }

    @Test
    public void validate_Exception_NullAlgorithm() {
        UploadTaskRequest request = UploadTaskRequest.builder()
            .name("test")
            .format("pmml")
            .algorithm(null)
            .body(bodyExample)
            .build();

        ActionRequestValidationException exception = request.validate();
        assertEquals("Validation Failed: 1: algorithm name can't be null or empty;", exception.getMessage());
    }

    @Test
    public void validate_Exception_NullBody() {
        UploadTaskRequest request = UploadTaskRequest.builder()
            .name("test")
            .format("pmml")
            .algorithm("isolationforest")
            .body(null)
            .build();

        ActionRequestValidationException exception = request.validate();
        assertEquals("Validation Failed: 1: model body can't be null or empty;2: can't retrieve model from body passed in;", exception.getMessage());
    }

    @Test
    public void validate_Exception_InvalidBody() {
        UploadTaskRequest request = UploadTaskRequest.builder()
            .name("test")
            .format("pmml")
            .algorithm("isolationforest")
            .body("123")
            .build();

        ActionRequestValidationException exception = request.validate();
        assertEquals("Validation Failed: 1: can't retrieve model from body passed in;", exception.getMessage());
    }

    @Test
    public void fromActionRequest_Success_WithUploadTaskRequest() {
        UploadTaskRequest request = UploadTaskRequest.builder()
            .name("test")
            .format("pmml")
            .algorithm("isolationforest")
            .body(bodyExample)
            .build();

        assertSame(UploadTaskRequest.fromActionRequest(request), request);
    }

    @Test
    public void fromActionRequest_Success_WithNonUploadTaskRequest() {
        UploadTaskRequest request = UploadTaskRequest.builder()
            .name("test")
            .format("pmml")
            .algorithm("isolationforest")
            .body(bodyExample)
            .build();

        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                request.writeTo(out);
            }
        };
        UploadTaskRequest result = UploadTaskRequest.fromActionRequest(actionRequest);
        assertNotSame(result, request);
        assertEquals(request.getName(), result.getName());
        assertEquals(request.getFormat(), result.getFormat());
        assertEquals(request.getAlgorithm(), result.getAlgorithm());
        assertEquals(request.getBody(), result.getBody());
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionRequest_IOException() throws IOException {
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException("test");
            }
        };
        UploadTaskRequest.fromActionRequest(actionRequest);
    }
}
