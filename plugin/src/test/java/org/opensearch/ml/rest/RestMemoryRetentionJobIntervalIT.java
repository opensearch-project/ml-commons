/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.ml.jobs.MLJobType;
import org.opensearch.ml.utils.TestHelper;

/**
 * Integration test proving that a live {@code PUT _cluster/settings} for
 * {@code plugins.ml_commons.memory.retention_job_interval_hours} updates the persisted memory-retention job
 * document's {@link org.opensearch.jobscheduler.spi.schedule.IntervalSchedule} — with no cluster restart.
 *
 * <p>The retention job is a write-once document in the {@code .plugins-ml-jobs} system index keyed by a fixed id.
 * The fix upserts that document (version-bumping) on a dynamic setting change, which JobScheduler's
 * {@code JobSweeper.postIndex} hook observes to deschedule + reschedule from the fresh interval.
 */
public class RestMemoryRetentionJobIntervalIT extends MLCommonsRestTestCase {

    private static final String JOBS_INDEX = ".plugins-ml-jobs";
    private static final String RETENTION_JOB_ID = MLJobType.MEMORY_RETENTION.name();
    private static final String INTERVAL_SETTING = "plugins.ml_commons.memory.retention_job_interval_hours";

    @Before
    public void setup() throws IOException {
        // Agentic memory must be enabled for the retention job to be scheduled.
        updateClusterSettings("plugins.ml_commons.agentic_memory_enabled", true);
    }

    @Test
    public void testUpdateIntervalRescheduleReflectedInPersistedDoc() throws Exception {
        // The retention job doc is created asynchronously when a 3.1+ data node is observed in the cluster state.
        assertBusy(() -> assertEquals(24, getPersistedIntervalHours()), 30, TimeUnit.SECONDS);

        // Live dynamic change: shrink the interval to 1 hour.
        updateClusterSettings(INTERVAL_SETTING, 1);

        // The elected cluster manager upserts the job doc; the persisted schedule interval should follow.
        assertBusy(() -> assertEquals(1, getPersistedIntervalHours()), 30, TimeUnit.SECONDS);
    }

    /**
     * Reads the persisted memory-retention job document from the {@code .plugins-ml-jobs} system index and returns
     * its {@code schedule.interval} value (in hours). Returns -1 when the document is not yet present.
     */
    @SuppressWarnings("unchecked")
    private int getPersistedIntervalHours() throws IOException {
        Response response;
        try {
            response = TestHelper.makeRequest(client(), "GET", JOBS_INDEX + "/_doc/" + RETENTION_JOB_ID, null, (String) null, null);
        } catch (ResponseException e) {
            // The jobs index or the document may not exist yet; treat as not-yet-present so assertBusy keeps polling.
            return -1;
        }
        Map<String, Object> responseMap = parseResponseToMap(response);
        if (responseMap == null || !Boolean.TRUE.equals(responseMap.get("found"))) {
            return -1;
        }
        Map<String, Object> source = (Map<String, Object>) responseMap.get("_source");
        Map<String, Object> schedule = (Map<String, Object>) source.get("schedule");
        Map<String, Object> interval = (Map<String, Object>) schedule.get("interval");
        return ((Number) interval.get("period")).intValue();
    }
}
