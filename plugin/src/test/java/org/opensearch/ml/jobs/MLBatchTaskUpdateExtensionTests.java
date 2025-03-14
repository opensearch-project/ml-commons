/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.jobs;

import java.io.IOException;

import org.junit.Ignore;
import org.junit.Test;

public class MLBatchTaskUpdateExtensionTests {

    @Test
    public void testBasic() {
        // MLBatchTaskUpdateExtension extension = new MLBatchTaskUpdateExtension();
        // assertEquals("checkBatchJobTaskStatus", extension.getJobType());
        // assertEquals(CommonValue.TASK_POLLING_JOB_INDEX, extension.getJobIndex());
        // assertEquals(MLJobRunner.getJobRunnerInstance(), extension.getJobRunner());
    }

    @Ignore
    @Test
    public void testParser() throws IOException {
        // MLBatchTaskUpdateExtension extension = new MLBatchTaskUpdateExtension();
        //
        // Instant enabledTime = Instant.now();
        // Instant lastUpdateTime = Instant.now();
        //
        // String json = "{"
        // + "\"name\": \"testJob\","
        // + "\"enabled\": true,"
        // + "\"enabled_time\": \""
        // + enabledTime.toString()
        // + "\","
        // + "\"last_update_time\": \""
        // + lastUpdateTime.toString()
        // + "\","
        // + "\"lock_duration_seconds\": 300,"
        // + "\"jitter\": 0.1"
        // + "}";
        //
        // XContentParser parser = XContentType.JSON
        // .xContent()
        // .createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.THROW_UNSUPPORTED_OPERATION, json);
        //
        // parser.nextToken();
        // MLJobParameter parsedJobParameter = (MLJobParameter) extension.getJobParser().parse(parser, "test_id", new JobDocVersion(1, 0,
        // 0));
        //
        // assertEquals("testJob", parsedJobParameter.getName());
        // assertTrue(parsedJobParameter.isEnabled());
    }

    @Test(expected = IOException.class)
    public void testParserWithInvalidJson() throws IOException {
        // MLBatchTaskUpdateExtension extension = new MLBatchTaskUpdateExtension();
        //
        // String invalidJson = "{ invalid json }";
        //
        // XContentParser parser = JsonXContent.jsonXContent.createParser(null, null, invalidJson);
        // extension.getJobParser().parse(parser, "test_id", new JobDocVersion(1, 0, 0));
    }
}
