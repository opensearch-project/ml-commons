/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

import java.util.concurrent.CompletionStage;

public class SdkClient {
    private SdkClient impl;
    
    public void setImplementation(SdkClient impl) {
        this.impl = impl;
    }

    public CompletionStage<PutCustomResponse> putCustom(PutCustomRequest request) {
        return impl.putCustom(request);
    }

    public CompletionStage<GetCustomResponse> getCustom(GetCustomRequest request) {
        return impl.getCustom(request);
    }

    public CompletionStage<DeleteCustomResponse> deleteCustom(DeleteCustomRequest request) {
        return impl.deleteCustom(request);
    }
}
