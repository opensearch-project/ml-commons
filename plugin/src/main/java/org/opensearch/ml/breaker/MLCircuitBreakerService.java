/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.breaker;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import lombok.extern.log4j.Log4j2;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.monitor.jvm.JvmService;
import org.opensearch.monitor.os.OsService;

/**
 * This service registers internal system breakers and provide API for users to register their own breakers.
 */
@Log4j2
public class MLCircuitBreakerService {

    private final ConcurrentMap<BreakerName, CircuitBreaker> breakers = new ConcurrentHashMap<>();
    private final JvmService jvmService;
    private final OsService osService;
    private final Settings settings;
    private final ClusterService clusterService;

    /**
     * Constructor.
     *
     * @param jvmService jvm info
     * @param osService os info
     * @param settings settings
     * @param clusterService clusterService
     */
    public MLCircuitBreakerService(JvmService jvmService, OsService osService, Settings settings, ClusterService clusterService) {
        this.jvmService = jvmService;
        this.osService = osService;
        this.settings = settings;
        this.clusterService = clusterService;
    }

    public void registerBreaker(BreakerName name, CircuitBreaker breaker) {
        breakers.putIfAbsent(name, breaker);
    }

    public void unregisterBreaker(BreakerName name) {
        if (name == null) {
            return;
        }

        breakers.remove(name);
        log.info("Removed ML breakers " + name);
    }

    public void clearBreakers() {
        breakers.clear();
        log.info("Cleared ML breakers.");
    }

    public CircuitBreaker getBreaker(BreakerName name) {
        return breakers.get(name);
    }

    /**
     * Initialize circuit breaker service.
     *
     * Register memory breaker by default.
     * @param path
     * @return MLCircuitBreakerService
     */
    public MLCircuitBreakerService init(Path path) {
        // Register memory circuit breaker
        registerBreaker(BreakerName.MEMORY, new MemoryCircuitBreaker(this.jvmService));
        log.info("Registered ML memory breaker.");
        registerBreaker(BreakerName.DISK, new DiskCircuitBreaker(path.toString()));
        log.info("Registered ML disk breaker.");
        // Register native memory circuit breaker
        registerBreaker(BreakerName.NATIVE_MEMORY, new NativeMemoryCircuitBreaker(this.osService, this.settings, this.clusterService));
        log.info("Registered ML native memory breaker.");

        return this;
    }

    /**
     *
     * @return any open circuit breaker; otherwise return null
     */
    public ThresholdCircuitBreaker checkOpenCB() {
        for (CircuitBreaker breaker : breakers.values()) {
            if (breaker.isOpen()) {
                return (ThresholdCircuitBreaker) breaker;
            }
        }

        return null;
    }
}
