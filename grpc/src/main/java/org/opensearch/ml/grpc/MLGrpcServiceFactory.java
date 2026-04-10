/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc;

import java.util.ArrayList;
import java.util.List;

import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.grpc.interfaces.MLClient;
import org.opensearch.ml.grpc.interfaces.MLModelAccessControlHelper;
import org.opensearch.ml.grpc.interfaces.MLModelManager;
import org.opensearch.ml.grpc.interfaces.MLSdkClient;
import org.opensearch.ml.grpc.interfaces.MLTaskRunner;
import org.opensearch.ml.grpc.interfaces.MLUserContextProvider;
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

    private static volatile MLModelManager modelManager;
    private static volatile MLTaskRunner predictTaskRunner;
    private static volatile MLTaskRunner executeTaskRunner;
    private static volatile MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private static volatile MLModelAccessControlHelper modelAccessControlHelper;
    private static volatile MLClient client;
    private static volatile MLSdkClient sdkClient;
    private static volatile MLUserContextProvider userContextProvider;

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
     * @param userContextProvider provider for extracting user from security context
     */
    public static void initialize(
        MLModelManager modelManager,
        MLTaskRunner predictTaskRunner,
        MLTaskRunner executeTaskRunner,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MLModelAccessControlHelper modelAccessControlHelper,
        MLClient client,
        MLSdkClient sdkClient,
        MLUserContextProvider userContextProvider
    ) {
        if (modelManager == null || predictTaskRunner == null || executeTaskRunner == null) {
            throw new IllegalArgumentException("Required dependencies cannot be null");
        }

        MLGrpcServiceFactory.modelManager = modelManager;
        MLGrpcServiceFactory.predictTaskRunner = predictTaskRunner;
        MLGrpcServiceFactory.executeTaskRunner = executeTaskRunner;
        MLGrpcServiceFactory.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        MLGrpcServiceFactory.modelAccessControlHelper = modelAccessControlHelper;
        MLGrpcServiceFactory.client = client;
        MLGrpcServiceFactory.sdkClient = sdkClient;
        MLGrpcServiceFactory.userContextProvider = userContextProvider;
    }

    @Override
    public String plugin() {
        return "opensearch-ml";
    }

    @Override
    public List<BindableService> build() {
        List<BindableService> services = new ArrayList<>();

        try {
            // Validate initialization
            if (modelManager == null || predictTaskRunner == null || executeTaskRunner == null) {
                throw new IllegalStateException("MLGrpcServiceFactory not initialized. Call initialize() first.");
            }

            // Create ML streaming service
            MLStreamingService streamingService = new MLStreamingService(
                modelManager,
                predictTaskRunner,
                executeTaskRunner,
                mlFeatureEnabledSetting,
                modelAccessControlHelper,
                client,
                sdkClient,
                userContextProvider
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
