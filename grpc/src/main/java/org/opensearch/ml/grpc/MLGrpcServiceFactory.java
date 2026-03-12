/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc;

import java.util.ArrayList;
import java.util.List;

import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.transport.grpc.spi.GrpcServiceFactory;

import io.grpc.BindableService;
import lombok.extern.log4j.Log4j2;

/**
 * Factory for registering ML Commons gRPC services with OpenSearch gRPC transport.
 *
 * <p>This factory integrates ML streaming services with OpenSearch's existing gRPC
 * infrastructure via the Service Provider Interface (SPI) mechanism.
 *
 * <p>Registered services:
 * <ul>
 *   <li>{@link MLStreamingService} - Unified streaming service for predictions and agent execution
 * </ul>
 *
 * <p>Registration is automatic via SPI:
 * {@code META-INF/services/org.opensearch.transport.grpc.spi.GrpcServiceFactory}
 */
@Log4j2
public class MLGrpcServiceFactory implements GrpcServiceFactory {

    // Static dependencies set by the ML plugin during initialization
    private static volatile Object modelManager;  // org.opensearch.ml.model.MLModelManager
    private static volatile Object predictTaskRunner;  // org.opensearch.ml.task.MLPredictTaskRunner
    private static volatile Object executeTaskRunner;  // org.opensearch.ml.task.MLExecuteTaskRunner
    private static volatile MLFeatureEnabledSetting mlFeatureEnabledSetting;

    /**
     * No-arg constructor required by SPI.
     * Dependencies must be initialized via {@link #initialize(Object, Object, Object, MLFeatureEnabledSetting)}
     * before build() is called.
     */
    public MLGrpcServiceFactory() {
        // SPI requires no-arg constructor
    }

    /**
     * Initializes the factory with ML Commons dependencies.
     *
     * <p>This must be called by the ML plugin during its initialization,
     * before the gRPC transport loads services.
     *
     * @param modelManager manager for model access and validation
     * @param predictTaskRunner task runner for executing predictions
     * @param executeTaskRunner task runner for executing agents
     * @param mlFeatureEnabledSetting feature flags for ML capabilities
     */
    public static void initialize(
        Object modelManager,
        Object predictTaskRunner,
        Object executeTaskRunner,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        MLGrpcServiceFactory.modelManager = modelManager;
        MLGrpcServiceFactory.predictTaskRunner = predictTaskRunner;
        MLGrpcServiceFactory.executeTaskRunner = executeTaskRunner;
        MLGrpcServiceFactory.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        log.info("ML gRPC service factory initialized with dependencies");
    }

    @Override
    public String plugin() {
        return "opensearch-ml";
    }

    @Override
    public List<BindableService> build() {
        List<BindableService> services = new ArrayList<>();

        // Check if streaming is enabled
        if (mlFeatureEnabledSetting == null || !mlFeatureEnabledSetting.isStreamEnabled()) {
            log.info("ML streaming disabled, skipping gRPC service registration");
            return services;
        }

        // Validate dependencies were initialized
        if (modelManager == null || predictTaskRunner == null || executeTaskRunner == null) {
            log.error("Cannot build ML gRPC services: dependencies not initialized. " + "Did you call MLGrpcServiceFactory.initialize()?");
            return services;
        }

        try {
            log.info("Building ML gRPC services (streaming enabled)");

            // Create and register unified ML streaming service
            MLStreamingService streamingService = new MLStreamingService(
                null,  // client - will be injected by gRPC transport if needed
                modelManager,
                predictTaskRunner,
                executeTaskRunner,
                mlFeatureEnabledSetting
            );

            services.add(streamingService);
            log.info("Registered MLStreamingService (PredictModelStream and ExecuteAgentStream RPCs)");

        } catch (Exception e) {
            log.error("Error creating ML gRPC services", e);
        }

        return services;
    }
}
