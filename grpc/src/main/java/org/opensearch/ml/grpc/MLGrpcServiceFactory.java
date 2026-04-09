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
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import lombok.extern.log4j.Log4j2;

/**
 * Factory for registering ML Commons gRPC services with OpenSearch gRPC transport.
 */
@Log4j2
public class MLGrpcServiceFactory implements GrpcServiceFactory {

    private static volatile Object modelManager;
    private static volatile Object predictTaskRunner;
    private static volatile Object executeTaskRunner;
    private static volatile MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private static volatile Object modelAccessControlHelper;
    private static volatile Object client;
    private static volatile Object sdkClient;

    /**
     * No-arg constructor required by SPI.
     * Dependencies must be initialized via {@link #initialize} before build() is called.
     */
    public MLGrpcServiceFactory() {
        // SPI requires no-arg constructor
    }

    /**
     * Initializes the factory with ML Commons dependencies.
     *
     * @param modelManager manager for model access and validation
     * @param predictTaskRunner task runner for executing predictions
     * @param executeTaskRunner task runner for executing agents
     * @param mlFeatureEnabledSetting feature flags for ML capabilities
     * @param modelAccessControlHelper helper for validating model access control
     * @param client OpenSearch client for validation
     * @param sdkClient SDK client for multi-tenant operations
     */
    public static void initialize(
        Object modelManager,
        Object predictTaskRunner,
        Object executeTaskRunner,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        Object modelAccessControlHelper,
        Object client,
        Object sdkClient
    ) {
        MLGrpcServiceFactory.modelManager = modelManager;
        MLGrpcServiceFactory.predictTaskRunner = predictTaskRunner;
        MLGrpcServiceFactory.executeTaskRunner = executeTaskRunner;
        MLGrpcServiceFactory.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        MLGrpcServiceFactory.modelAccessControlHelper = modelAccessControlHelper;
        MLGrpcServiceFactory.client = client;
        MLGrpcServiceFactory.sdkClient = sdkClient;
    }

    @Override
    public String plugin() {
        return "opensearch-ml";
    }

    @Override
    public List<BindableService> build() {
        List<BindableService> services = new ArrayList<>();

        try {
            // Create ML streaming service
            MLStreamingService streamingService = new MLStreamingService(
                modelManager,
                predictTaskRunner,
                executeTaskRunner,
                mlFeatureEnabledSetting,
                modelAccessControlHelper,
                client,
                sdkClient
            );

            // Wrap service with tenant ID interceptor to extract metadata
            ServerServiceDefinition interceptedDefinition = ServerInterceptors.intercept(streamingService, new TenantIdInterceptor());

            BindableService interceptedService = new BindableService() {
                @Override
                public ServerServiceDefinition bindService() {
                    return interceptedDefinition;
                }
            };

            services.add(interceptedService);
        } catch (Exception e) {
            log.error("Error creating ML gRPC services", e);
        }
        return services;
    }
}
