/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.resources;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;

import java.util.Set;

import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.security.spi.resources.ResourceProvider;
import org.opensearch.security.spi.resources.ResourceSharingExtension;
import org.opensearch.security.spi.resources.client.ResourceSharingClient;

public class MLResourceSharingExtension implements ResourceSharingExtension {

    private ResourceSharingClient resourceSharingClient;

    @Override
    public Set<ResourceProvider> getResourceProviders() {
        return Set.of(new ResourceProvider(MLModelGroup.class.getCanonicalName(), ML_MODEL_GROUP_INDEX));
    }

    @Override
    public void assignResourceSharingClient(ResourceSharingClient resourceSharingClient) {
        this.resourceSharingClient = resourceSharingClient;
    }

    @Override
    public ResourceSharingClient getResourceSharingClient() {
        return resourceSharingClient;
    }
}
