/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.breaker;

import java.io.File;

/**
 * A circuit breaker for disk usage.
 */
public class DiskCircuitBreaker extends ThresholdCircuitBreaker<Long> {

    public static final long DEFAULT_DISK_SHORTAGE_THRESHOLD = 10L;
    public static final String DEFAULT_DISK_DIR = "/";
    private String diskDir;

    public DiskCircuitBreaker() {
        super(DEFAULT_DISK_SHORTAGE_THRESHOLD);
        this.diskDir = DEFAULT_DISK_DIR;
    }

    public DiskCircuitBreaker(String diskDir) {
        super(DEFAULT_DISK_SHORTAGE_THRESHOLD);
        this.diskDir = diskDir;
    }

    public DiskCircuitBreaker(long threshold, String diskDir) {
        super(threshold);
        this.diskDir = diskDir;
    }

    @Override
    public boolean isOpen() {
        return (new File(diskDir).getFreeSpace()/1024/1024/1024) < getThreshold();  // in GB
    }
}