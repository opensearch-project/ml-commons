/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.breaker;

import org.opensearch.ml.common.exception.MLException;

import java.io.File;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

/**
 * A circuit breaker for disk usage.
 */
public class DiskCircuitBreaker extends ThresholdCircuitBreaker<Long> {
    private static final String ML_DISK_CB = "Disk Circuit Breaker";
    public static final long DEFAULT_DISK_SHORTAGE_THRESHOLD = 10L;
    private String diskDir;

    public DiskCircuitBreaker(String diskDir) {
        super(DEFAULT_DISK_SHORTAGE_THRESHOLD);
        this.diskDir = diskDir;
    }

    public DiskCircuitBreaker(long threshold, String diskDir) {
        super(threshold);
        this.diskDir = diskDir;
    }

    @Override
    public String getName() {
        return  ML_DISK_CB;
    }

    @Override
    public boolean isOpen() {
        try {
            return AccessController.doPrivileged((PrivilegedExceptionAction<Boolean>) () -> {
                return (new File(diskDir).getFreeSpace()/1024/1024/1024) < getThreshold();  // in GB
            });
        } catch (PrivilegedActionException e) {
            throw new MLException("Failed to run disk circuit breaker");
        }
    }
}