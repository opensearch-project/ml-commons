/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.resources;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;

import java.util.Iterator;
import java.util.Set;

import org.junit.After;
import org.junit.Test;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.ml.common.ResourceSharingClientAccessor;
import org.opensearch.security.spi.resources.ResourceProvider;
import org.opensearch.security.spi.resources.client.ResourceSharingClient;

public class MLResourceSharingExtensionTests {
    private static String extractIndexFrom(Set<ResourceProvider> providers) {
        assertThat("providers should not be null", providers, is(not(nullValue())));
        assertThat("Expected exactly one provider", providers.size(), equalTo(1));
        Iterator<ResourceProvider> it = providers.iterator();
        assertThat(it.hasNext(), equalTo(true));
        return it.next().resourceIndexName();
    }

    @After
    public void tearDown() {
        // Reset the accessor to avoid cross-test leakage
        ResourceSharingClientAccessor.getInstance().setResourceSharingClient(null);
    }

    @Test
    public void testGetResourceProviders_returnsExpectedSingleProvider() {
        MLResourceSharingExtension ext = new MLResourceSharingExtension();

        Set<ResourceProvider> providers = ext.getResourceProviders();
        assertThat(providers, is(not(nullValue())));
        assertThat(providers.size(), equalTo(1));

        ResourceProvider provider = providers.iterator().next();
        assertThat(
            "Resource type should be MLModelGroup canonical name",
            provider.resourceType(),
            equalTo(MLModelGroup.class.getCanonicalName())
        );

        String index = provider.resourceIndexName();
        assertThat("Index must not be empty", index.trim(), equalTo(CommonValue.ML_MODEL_GROUP_INDEX));

    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetResourceProviders_returnsUnmodifiableSet() {
        MLResourceSharingExtension ext = new MLResourceSharingExtension();
        Set<ResourceProvider> providers = ext.getResourceProviders();

        // Attempt to modify — Set.of(...) should be unmodifiable and throw
        providers.add(new ResourceProvider("some.Type", "some-index"));
    }

    @Test
    public void testAssignResourceSharingClient_setsClientOnAccessor() {
        MLResourceSharingExtension ext = new MLResourceSharingExtension();
        ResourceSharingClient mockClient = mock(ResourceSharingClient.class);

        assertThat(ResourceSharingClientAccessor.getInstance().getResourceSharingClient(), is(nullValue()));

        ext.assignResourceSharingClient(mockClient);

        assertThat(
            "Accessor should hold the client passed to extension",
            ResourceSharingClientAccessor.getInstance().getResourceSharingClient(),
            equalTo(mockClient)
        );
    }

    @Test
    public void testAssignResourceSharingClient_overwritesExistingClient() {
        MLResourceSharingExtension ext = new MLResourceSharingExtension();
        ResourceSharingClient first = mock(ResourceSharingClient.class);
        ResourceSharingClient second = mock(ResourceSharingClient.class);

        // Prime with the first client
        ResourceSharingClientAccessor.getInstance().setResourceSharingClient(first);
        assertThat(ResourceSharingClientAccessor.getInstance().getResourceSharingClient(), equalTo(first));

        // Now assign a new one via the extension
        ext.assignResourceSharingClient(second);

        assertThat(
            "Accessor should be updated to the new client",
            ResourceSharingClientAccessor.getInstance().getResourceSharingClient(),
            equalTo(second)
        );
    }

    @Test
    public void testGetResourceProviders_isDeterministicAcrossCalls() {
        MLResourceSharingExtension ext = new MLResourceSharingExtension();

        Set<ResourceProvider> first = ext.getResourceProviders();
        Set<ResourceProvider> second = ext.getResourceProviders();

        // Same contents
        assertThat(first, equalTo(second));

        // Extract and compare details for additional safety
        String idx1 = extractIndexFrom(first);
        String idx2 = extractIndexFrom(second);
        assertThat(idx1, equalTo(idx2));
    }
}
