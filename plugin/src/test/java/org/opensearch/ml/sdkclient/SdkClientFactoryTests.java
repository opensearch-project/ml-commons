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

import org.opensearch.OpenSearchException;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.sdk.SdkClient;
import org.opensearch.sdk.SdkClientSettings;
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
            .put(SdkClientSettings.REMOTE_METADATA_TYPE_KEY, SdkClientSettings.REMOTE_OPENSEARCH)
            .put(SdkClientSettings.REMOTE_METADATA_ENDPOINT_KEY, "http://example.org")
            .put(SdkClientSettings.REMOTE_METADATA_REGION_KEY, "eu-west-3")
            .build();
        SdkClient sdkClient = SdkClientFactory.createSdkClient(mock(Client.class), NamedXContentRegistry.EMPTY, settings);
        assertTrue(sdkClient.getDelegate() instanceof RemoteClusterIndicesClient);
    }

    public void testAwsOpenSearchServiceBinding() {
        Settings settings = Settings
            .builder()
            .put(SdkClientSettings.REMOTE_METADATA_TYPE_KEY, SdkClientSettings.AWS_OPENSEARCH_SERVICE)
            .put(SdkClientSettings.REMOTE_METADATA_ENDPOINT_KEY, "example.org")
            .put(SdkClientSettings.REMOTE_METADATA_REGION_KEY, "eu-west-3")
            .build();
        SdkClient sdkClient = SdkClientFactory.createSdkClient(mock(Client.class), NamedXContentRegistry.EMPTY, settings);
        assertTrue(sdkClient.getDelegate() instanceof RemoteClusterIndicesClient);
    }

    public void testDDBBinding() {
        Settings settings = Settings
            .builder()
            .put(SdkClientSettings.REMOTE_METADATA_TYPE_KEY, SdkClientSettings.AWS_DYNAMO_DB)
            .put(SdkClientSettings.REMOTE_METADATA_ENDPOINT_KEY, "http://example.org")
            .put(SdkClientSettings.REMOTE_METADATA_REGION_KEY, "eu-west-3")
            .build();
        SdkClient sdkClient = SdkClientFactory.createSdkClient(mock(Client.class), NamedXContentRegistry.EMPTY, settings);
        assertTrue(sdkClient.getDelegate() instanceof DDBOpenSearchClient);
    }

    public void testRemoteOpenSearchBindingException() {
        Settings settings = Settings.builder().put(SdkClientSettings.REMOTE_METADATA_TYPE_KEY, SdkClientSettings.REMOTE_OPENSEARCH).build();
        assertThrows(
            OpenSearchException.class,
            () -> SdkClientFactory.createSdkClient(mock(Client.class), NamedXContentRegistry.EMPTY, settings)
        );
    }

    public void testAwsOpenSearchServiceBindingException() {
        Settings settings = Settings
            .builder()
            .put(SdkClientSettings.REMOTE_METADATA_TYPE_KEY, SdkClientSettings.AWS_OPENSEARCH_SERVICE)
            .build();
        assertThrows(
            OpenSearchException.class,
            () -> SdkClientFactory.createSdkClient(mock(Client.class), NamedXContentRegistry.EMPTY, settings)
        );
    }

    public void testDDBBindingException() {
        Settings settings = Settings.builder().put(SdkClientSettings.REMOTE_METADATA_TYPE_KEY, SdkClientSettings.AWS_DYNAMO_DB).build();
        assertThrows(
            OpenSearchException.class,
            () -> SdkClientFactory.createSdkClient(mock(Client.class), NamedXContentRegistry.EMPTY, settings)
        );
    }
}
