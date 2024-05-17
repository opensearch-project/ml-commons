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

import org.opensearch.common.inject.AbstractModule;
import org.opensearch.common.inject.Guice;
import org.opensearch.common.inject.Injector;
import org.opensearch.common.inject.Module;
import org.opensearch.sdk.SdkClient;
import org.opensearch.test.OpenSearchTestCase;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;

@ThreadLeakScope(ThreadLeakScope.Scope.NONE) // remote http client is never closed
public class SdkClientModuleTests extends OpenSearchTestCase {

    private Module localClientModule = new AbstractModule() {
        @Override
        protected void configure() {
            bind(LocalClusterIndicesClient.class).toInstance(mock(LocalClusterIndicesClient.class));
        }
    };

    public void testLocalBinding() {
        Injector injector = Guice.createInjector(new SdkClientModule(null, null), localClientModule);

        SdkClient sdkClient = injector.getInstance(SdkClient.class);
        assertTrue(sdkClient instanceof LocalClusterIndicesClient);
    }

    public void testRemoteBinding() {
        Injector injector = Guice.createInjector(new SdkClientModule("http://example.org", "eu-west-3"));

        SdkClient sdkClient = injector.getInstance(SdkClient.class);
        assertTrue(sdkClient instanceof RemoteClusterIndicesClient);
    }
}
