/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.breaker;

import lombok.extern.log4j.Log4j2;
import org.opensearch.monitor.jvm.JvmService;
import org.opensearch.monitor.os.OsService;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * This service registers internal system breakers and provide API for users to register their own breakers.
 */
@Log4j2
public class MLCircuitBreakerService {

    private final ConcurrentMap<BreakerName, CircuitBreaker> breakers = new ConcurrentHashMap<>();
    private final JvmService jvmService;
    private final OsService osService;

    /**
     * Constructor.
     *
     * @param jvmService jvm info
     */
    public MLCircuitBreakerService(JvmService jvmService, OsService osService) {
        this.jvmService = jvmService;
        this.osService = osService;
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
        registerBreaker(BreakerName.NATIVE_MEMORY, new NativeMemoryCircuitBreaker(this.osService));
        log.info("Registered ML native memory breaker.");

        return this;
    }

    /**
     *
     * @return the name of any open circuit breaker; otherwise return null
     */
    public String checkOpenCB() {
        for (CircuitBreaker breaker : breakers.values()) {
            if (breaker.isOpen()) {
                return breaker.getName();
            }
        }

        return null;
    }
}
