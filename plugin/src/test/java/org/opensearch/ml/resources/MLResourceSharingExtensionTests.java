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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Test;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.ResourceSharingClientAccessor;
import org.opensearch.security.spi.resources.ResourceProvider;
import org.opensearch.security.spi.resources.client.ResourceSharingClient;

public class MLResourceSharingExtensionTests {
    private static Map<String, String> indicesByType(Set<ResourceProvider> providers) {
        assertThat("providers should not be null", providers, is(not(nullValue())));
        Map<String, String> byType = new HashMap<>();
        for (ResourceProvider provider : providers) {
            byType.put(provider.resourceType(), provider.resourceIndexName());
        }
        return byType;
    }

    private static ResourceProvider providerFor(Set<ResourceProvider> providers, String resourceType) {
        return providers
            .stream()
            .filter(p -> resourceType.equals(p.resourceType()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No provider registered for " + resourceType));
    }

    @After
    public void tearDown() {
        // Reset the accessor to avoid cross-test leakage
        ResourceSharingClientAccessor.getInstance().setResourceSharingClient(null);
    }

    private static Object getResourceSharingClient() {
        return ResourceSharingClientAccessor.getInstance().getResourceSharingClient();
    }

    @Test
    public void testGetResourceProviders_returnsModelGroupAndModelProviders() {
        MLResourceSharingExtension ext = new MLResourceSharingExtension();

        Set<ResourceProvider> providers = ext.getResourceProviders();
        assertThat(providers, is(not(nullValue())));
        assertThat(providers.size(), equalTo(2));

        Map<String, String> indicesByType = indicesByType(providers);
        assertThat(indicesByType.get(CommonValue.ML_MODEL_GROUP_RESOURCE_TYPE), equalTo(CommonValue.ML_MODEL_GROUP_INDEX));
        assertThat(indicesByType.get(CommonValue.ML_MODEL_RESOURCE_TYPE), equalTo(CommonValue.ML_MODEL_INDEX));
    }

    @Test
    public void testModelProviderDeclarations() {
        Set<ResourceProvider> providers = new MLResourceSharingExtension().getResourceProviders();
        ResourceProvider model = providerFor(providers, CommonValue.ML_MODEL_RESOURCE_TYPE);

        // The framework returns the extracted typeField value as the resource type, so the stamped value must be
        // the type identifier itself — see MLModel.toXContent.
        assertThat(model.typeField(), equalTo(CommonValue.RESOURCE_TYPE_FIELD));
        assertThat(model.parentType(), equalTo(CommonValue.ML_MODEL_GROUP_RESOURCE_TYPE));
        assertThat(model.parentIdField(), equalTo(MLModel.MODEL_GROUP_ID_FIELD));
        assertThat(model.ownerNamePath(), equalTo("/user/name"));
        assertThat(model.ownerBackendRolesPath(), equalTo("/user/backend_roles"));
        assertThat("model index maps no workspaces field", model.workspacesField(), is(nullValue()));
    }

    @Test
    public void testModelGroupProviderHasNoTypeFieldOrParent() {
        Set<ResourceProvider> providers = new MLResourceSharingExtension().getResourceProviders();
        ResourceProvider group = providerFor(providers, CommonValue.ML_MODEL_GROUP_RESOURCE_TYPE);

        // The group index holds a single resource type, so no discriminator is needed and it has no parent.
        assertThat(group.typeField(), is(nullValue()));
        assertThat(group.parentType(), is(nullValue()));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetResourceProviders_returnsUnmodifiableSet() {
        MLResourceSharingExtension ext = new MLResourceSharingExtension();
        Set<ResourceProvider> providers = ext.getResourceProviders();

        // Attempt to modify — Set.of(...) should be unmodifiable and throw
        providers.add(new ResourceProvider() {
            @Override
            public String resourceType() {
                return "exampleType";
            }

            @Override
            public String resourceIndexName() {
                return "some-index";
            }
        });
    }

    @Test
    public void testAssignResourceSharingClient_setsClientOnAccessor() {
        MLResourceSharingExtension ext = new MLResourceSharingExtension();
        ResourceSharingClient mockClient = mock(ResourceSharingClient.class);

        assertThat(getResourceSharingClient(), is(nullValue()));

        ext.assignResourceSharingClient(mockClient);

        assertThat("Accessor should hold the client passed to extension", getResourceSharingClient(), equalTo(mockClient));
    }

    @Test
    public void testAssignResourceSharingClient_overwritesExistingClient() {
        MLResourceSharingExtension ext = new MLResourceSharingExtension();
        ResourceSharingClient first = mock(ResourceSharingClient.class);
        ResourceSharingClient second = mock(ResourceSharingClient.class);

        // Prime with the first client
        ResourceSharingClientAccessor.getInstance().setResourceSharingClient(first);
        assertThat(getResourceSharingClient(), equalTo(first));

        // Now assign a new one via the extension
        ext.assignResourceSharingClient(second);

        assertThat("Accessor should be updated to the new client", getResourceSharingClient(), equalTo(second));
    }

    @Test
    public void testGetResourceProviders_isDeterministicAcrossCalls() {
        MLResourceSharingExtension ext = new MLResourceSharingExtension();

        Set<ResourceProvider> first = ext.getResourceProviders();
        Set<ResourceProvider> second = ext.getResourceProviders();

        // Same contents
        assertThat(first, equalTo(second));

        // Extract and compare details for additional safety
        assertThat(indicesByType(first), equalTo(indicesByType(second)));
    }
}
