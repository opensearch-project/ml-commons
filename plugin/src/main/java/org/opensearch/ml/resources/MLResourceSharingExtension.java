/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.resources;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_RESOURCE_TYPE;

import java.util.Set;

import org.opensearch.ml.common.ResourceSharingClientAccessor;
import org.opensearch.security.spi.resources.ResourceProvider;
import org.opensearch.security.spi.resources.ResourceSharingExtension;
import org.opensearch.security.spi.resources.client.ResourceSharingClient;

public class MLResourceSharingExtension implements ResourceSharingExtension {

    private static final ResourceProvider MODEL_GROUP_PROVIDER = new ResourceProvider() {

        @Override
        public String resourceType() {
            return ML_MODEL_GROUP_RESOURCE_TYPE;
        }

        @Override
        public String resourceIndexName() {
            return ML_MODEL_GROUP_INDEX;
        }
    };

    @Override
    public Set<ResourceProvider> getResourceProviders() {
        return Set.of(MODEL_GROUP_PROVIDER);
    }

    @Override
    public void assignResourceSharingClient(ResourceSharingClient resourceSharingClient) {
        ResourceSharingClientAccessor.getInstance().setResourceSharingClient(resourceSharingClient);
    }
}
