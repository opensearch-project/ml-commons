/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import org.opensearch.security.spi.resources.client.NoopResourceSharingClient;
import org.opensearch.security.spi.resources.client.ResourceSharingClient;

/**
 * Accessor for resource sharing client
 */
public class ResourceSharingClientAccessor {
    private static ResourceSharingClient CLIENT;

    private ResourceSharingClientAccessor() {}

    /**
     * Get resource sharing client
     *
     * @return resource sharing client, NoopResourceSharingClient when security is disabled
     */
    public static ResourceSharingClient getResourceSharingClient() {
        return CLIENT == null ? new NoopResourceSharingClient() : CLIENT;
    }

    /**
     * Set resource sharing client
     *
     * @param client resource sharing client
     */
    public static void setResourceSharingClient(ResourceSharingClient client) {
        CLIENT = client;
    }

}
