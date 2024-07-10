/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.ml.sdkclient;

import static org.mockito.Mockito.mock;
import static org.opensearch.ml.sdkclient.SdkClientFactory.AWS_DYNAMO_DB;
import static org.opensearch.ml.sdkclient.SdkClientFactory.AWS_OPENSEARCH_SERVICE;
import static org.opensearch.ml.sdkclient.SdkClientFactory.REGION;
import static org.opensearch.ml.sdkclient.SdkClientFactory.REMOTE_METADATA_ENDPOINT;
import static org.opensearch.ml.sdkclient.SdkClientFactory.REMOTE_METADATA_TYPE;
import static org.opensearch.ml.sdkclient.SdkClientFactory.REMOTE_OPENSEARCH;

import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.sdk.SdkClient;
import org.opensearch.test.OpenSearchTestCase;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;

@ThreadLeakScope(ThreadLeakScope.Scope.NONE) // remote http client is never closed
public class SdkClientFactoryTests extends OpenSearchTestCase {

    public void testLocalBinding() {
        Settings settings = Settings.builder().build();
        SdkClient sdkClient = SdkClientFactory.createSdkClient(mock(Client.class), NamedXContentRegistry.EMPTY, settings);
        assertTrue(sdkClient.getDelegate() instanceof LocalClusterIndicesClient);
    }

    public void testRemoteOpenSearchBinding() {
        Settings settings = Settings
            .builder()
            .put(REMOTE_METADATA_TYPE, REMOTE_OPENSEARCH)
            .put(REMOTE_METADATA_ENDPOINT, "http://example.org")
            .put(REGION, "eu-west-3")
            .build();
        SdkClient sdkClient = SdkClientFactory.createSdkClient(mock(Client.class), NamedXContentRegistry.EMPTY, settings);
        assertTrue(sdkClient.getDelegate() instanceof RemoteClusterIndicesClient);
    }

    public void testAwsOpenSearchServiceBinding() {
        Settings settings = Settings
            .builder()
            .put(REMOTE_METADATA_TYPE, AWS_OPENSEARCH_SERVICE)
            .put(REMOTE_METADATA_ENDPOINT, "example.org")
            .put(REGION, "eu-west-3")
            .build();
        SdkClient sdkClient = SdkClientFactory.createSdkClient(mock(Client.class), NamedXContentRegistry.EMPTY, settings);
        assertTrue(sdkClient.getDelegate() instanceof RemoteClusterIndicesClient);
    }

    public void testDDBBinding() {
        Settings settings = Settings
            .builder()
            .put(REMOTE_METADATA_TYPE, AWS_DYNAMO_DB)
            .put(REMOTE_METADATA_ENDPOINT, "http://example.org")
            .put(REGION, "eu-west-3")
            .build();
        SdkClient sdkClient = SdkClientFactory.createSdkClient(mock(Client.class), NamedXContentRegistry.EMPTY, settings);
        assertTrue(sdkClient.getDelegate() instanceof DDBOpenSearchClient);
    }
}
