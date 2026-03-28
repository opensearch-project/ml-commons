/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.jobs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.jobscheduler.spi.schedule.IntervalSchedule;

public class MLJobParameterTests {

    private MLJobParameter jobParameter;
    private String jobName;
    private IntervalSchedule schedule;
    private Long lockDurationSeconds;
    private Double jitter;
    private MLJobType jobType;

    @Before
    public void setUp() {
        jobName = "test-job";
        schedule = new IntervalSchedule(Instant.now(), 1, ChronoUnit.MINUTES);
        lockDurationSeconds = 20L;
        jitter = 0.5;
        jobType = null;
        jobParameter = new MLJobParameter(jobName, schedule, lockDurationSeconds, jitter, jobType, true);
    }

    @Test
    public void testToXContent() throws Exception {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        jobParameter.toXContent(builder, null);
        String jsonString = builder.toString();

        assertTrue(jsonString.contains(jobName));
        assertTrue(jsonString.contains("enabled"));
        assertTrue(jsonString.contains("schedule"));
        assertTrue(jsonString.contains("lock_duration_seconds"));
        assertTrue(jsonString.contains("jitter"));
    }

    @Test
    public void testNullCase() throws IOException {
        String newJobName = "test-job";
        MLJobParameter nullParameter = new MLJobParameter(newJobName, null, null, null, null, true);
        nullParameter.setLastUpdateTime(null);
        nullParameter.setEnabledTime(null);

        XContentBuilder builder = XContentFactory.jsonBuilder();
        nullParameter.toXContent(builder, null);
        String jsonString = builder.toString();

        assertTrue(jsonString.contains(newJobName));
        assertEquals(newJobName, nullParameter.getName());
        assertTrue(nullParameter.isEnabled());
        assertNull(nullParameter.getSchedule());
        assertNull(nullParameter.getLockDurationSeconds());
        assertNull(nullParameter.getJitter());
        assertNull(nullParameter.getJobType());
    }
}
