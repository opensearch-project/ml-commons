/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.breaker;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_DISK_FREE_SPACE_THRESHOLD;

import java.io.File;
import java.util.Optional;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.common.unit.ByteSizeUnit;
import org.opensearch.core.common.unit.ByteSizeValue;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.secure_sm.AccessController;

/**
 * A circuit breaker for disk usage.
 */
public class DiskCircuitBreaker extends ThresholdCircuitBreaker<ByteSizeValue> {
    private static final String ML_DISK_CB = "Disk Circuit Breaker";
    public static final ByteSizeValue DEFAULT_DISK_SHORTAGE_THRESHOLD = new ByteSizeValue(5, ByteSizeUnit.GB);
    private final File diskDir;

    public DiskCircuitBreaker(Settings settings, ClusterService clusterService, File diskDir) {
        super(Optional.ofNullable(ML_COMMONS_DISK_FREE_SPACE_THRESHOLD.get(settings)).orElse(DEFAULT_DISK_SHORTAGE_THRESHOLD));
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_DISK_FREE_SPACE_THRESHOLD, super::setThreshold);
        this.diskDir = diskDir;
    }

    @Override
    public String getName() {
        return ML_DISK_CB;
    }

    @Override
    public boolean isOpen() {
        try {
            return AccessController.doPrivilegedChecked(() -> {
                return new ByteSizeValue(diskDir.getFreeSpace(), ByteSizeUnit.BYTES).compareTo(getThreshold()) < 0;  // in GB
            });
        } catch (Exception e) {
            throw new MLException("Failed to run disk circuit breaker");
        }
    }
}
