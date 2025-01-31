/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.jobs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.Instant;

import org.junit.Ignore;
import org.junit.Test;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.jobscheduler.spi.JobDocVersion;
import org.opensearch.ml.common.CommonValue;

public class MLBatchTaskUpdateExtensionTests {

    @Test
    public void testBasic() {
        MLBatchTaskUpdateExtension extension = new MLBatchTaskUpdateExtension();
        assertEquals("checkBatchJobTaskStatus", extension.getJobType());
        assertEquals(CommonValue.TASK_POLLING_JOB_INDEX, extension.getJobIndex());
        assertEquals(MLBatchTaskUpdateJobRunner.getJobRunnerInstance(), extension.getJobRunner());
    }

    @Ignore
    @Test
    public void testParser() throws IOException {
        MLBatchTaskUpdateExtension extension = new MLBatchTaskUpdateExtension();

        Instant enabledTime = Instant.now();
        Instant lastUpdateTime = Instant.now();

        String json = "{"
            + "\"name\": \"testJob\","
            + "\"enabled\": true,"
            + "\"enabled_time\": \""
            + enabledTime.toString()
            + "\","
            + "\"last_update_time\": \""
            + lastUpdateTime.toString()
            + "\","
            + "\"lock_duration_seconds\": 300,"
            + "\"jitter\": 0.1"
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.THROW_UNSUPPORTED_OPERATION, json);

        parser.nextToken();
        MLBatchTaskUpdateJobParameter parsedJobParameter = (MLBatchTaskUpdateJobParameter) extension
            .getJobParser()
            .parse(parser, "test_id", new JobDocVersion(1, 0, 0));

        assertEquals("testJob", parsedJobParameter.getName());
        assertTrue(parsedJobParameter.isEnabled());
    }

    @Test(expected = IOException.class)
    public void testParserWithInvalidJson() throws IOException {
        MLBatchTaskUpdateExtension extension = new MLBatchTaskUpdateExtension();

        String invalidJson = "{ invalid json }";

        XContentParser parser = JsonXContent.jsonXContent.createParser(null, null, invalidJson);
        extension.getJobParser().parse(parser, "test_id", new JobDocVersion(1, 0, 0));
    }
}
