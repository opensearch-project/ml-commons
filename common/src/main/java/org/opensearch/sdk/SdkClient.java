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

public interface SdkClient {

    CompletionStage<PutCustomResponse> putCustom(PutCustomRequest request);

    CompletionStage<GetCustomResponse> getCustom(GetCustomRequest request);

    CompletionStage<DeleteCustomResponse> deleteCustom(DeleteCustomRequest request);
}
