/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.jobs;

import static org.junit.Assert.*;

import java.io.IOException;
import java.time.Instant;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.jobscheduler.spi.schedule.IntervalSchedule;

public class MLBatchTaskUpdateJobParameterTests {

    private MLJobParameter jobParameter;
    private String jobName;
    private IntervalSchedule schedule;
    private Long lockDurationSeconds;
    private Double jitter;

    @Before
    public void setUp() {
        // jobName = "test-job";
        // schedule = new IntervalSchedule(Instant.now(), 1, ChronoUnit.MINUTES);
        // lockDurationSeconds = 20L;
        // jitter = 0.5;
        // jobParameter = new MLJobParameter(jobName, schedule, lockDurationSeconds, jitter);
    }

    @Test
    public void testConstructor() {
        assertNotNull(jobParameter);
        assertEquals(jobName, jobParameter.getName());
        assertEquals(schedule, jobParameter.getSchedule());
        assertEquals(lockDurationSeconds, jobParameter.getLockDurationSeconds());
        assertEquals(jitter, jobParameter.getJitter());
        assertTrue(jobParameter.isEnabled());
        assertNotNull(jobParameter.getEnabledTime());
        assertNotNull(jobParameter.getLastUpdateTime());
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
    public void testSetters() {
        String newJobName = "new-job";
        jobParameter.setJobName(newJobName);
        assertEquals(newJobName, jobParameter.getName());

        Instant newTime = Instant.now();
        jobParameter.setLastUpdateTime(newTime);
        assertEquals(newTime, jobParameter.getLastUpdateTime());

        jobParameter.setEnabled(false);
        assertEquals(false, jobParameter.isEnabled());

        Long newLockDuration = 30L;
        jobParameter.setLockDurationSeconds(newLockDuration);
        assertEquals(newLockDuration, jobParameter.getLockDurationSeconds());

        Double newJitter = 0.7;
        jobParameter.setJitter(newJitter);
        assertEquals(newJitter, jobParameter.getJitter());
    }

    @Test
    public void testNullCase() throws IOException {
        String newJobName = "test-job";

        // jobParameter = new MLJobParameter(newJobName, null, null, null);
        // jobParameter.setLastUpdateTime(null);
        // jobParameter.setEnabledTime(null);
        //
        // XContentBuilder builder = XContentFactory.jsonBuilder();
        // jobParameter.toXContent(builder, null);
        // String jsonString = builder.toString();
        //
        // assertTrue(jsonString.contains(jobName));
        // assertEquals(newJobName, jobParameter.getName());
    }
}
