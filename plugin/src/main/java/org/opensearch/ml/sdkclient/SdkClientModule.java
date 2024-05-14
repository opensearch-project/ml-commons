/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.ml.sdkclient;

import org.opensearch.common.inject.AbstractModule;
import org.opensearch.sdk.SdkClient;

public class SdkClientModule extends AbstractModule {

    @Override
    protected void configure() {
        // TODO use setting to switch this to different client
        bind(SdkClient.class).to(LocalClusterIndicesClient.class);
    }

}
