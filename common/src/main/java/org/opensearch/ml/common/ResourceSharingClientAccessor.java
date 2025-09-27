/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import org.opensearch.security.spi.resources.client.ResourceSharingClient;

/**
 * Accessor for resource sharing client
 */
public class ResourceSharingClientAccessor {
    private ResourceSharingClient CLIENT;

    private static ResourceSharingClientAccessor resourceSharingClientAccessor;

    private ResourceSharingClientAccessor() {}

    public static ResourceSharingClientAccessor getInstance() {
        if (resourceSharingClientAccessor == null) {
            resourceSharingClientAccessor = new ResourceSharingClientAccessor();
        }

        return resourceSharingClientAccessor;
    }

    /**
     * Set the resource sharing client
     */
    public void setResourceSharingClient(ResourceSharingClient client) {
        resourceSharingClientAccessor.CLIENT = client;
    }

    /**
     * Get the resource sharing client
     */
    public ResourceSharingClient getResourceSharingClient() {
        return resourceSharingClientAccessor.CLIENT;
    }

}
