/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.resources;

import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_RESOURCE_TYPE;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_RESOURCE_TYPE;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_RESOURCE_TYPE;
import static org.opensearch.ml.common.CommonValue.RESOURCE_TYPE_FIELD;
import static org.opensearch.ml.common.MLModel.MODEL_GROUP_ID_FIELD;

import java.util.Set;

import org.opensearch.ml.common.ResourceSharingClientAccessor;
import org.opensearch.security.spi.resources.ResourceProvider;
import org.opensearch.security.spi.resources.ResourceSharingExtension;
import org.opensearch.security.spi.resources.client.ResourceSharingClient;

public class MLResourceSharingExtension implements ResourceSharingExtension {

    /**
     * Sharing a model group is deprecated: models are the shareable unit now, and this registration is slated for
     * removal in 4.0. It stays registered until then so existing group shares keep working, and so the migration can
     * derive each model's owner and recipients from its group's sharing record.
     */
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

    /**
     * Models are shareable in their own right. The parent link to the model group is declared so that a model written
     * under a system subject inherits the group's owner, and so a point check on a model falls back to the group; it
     * does not affect which models a search returns.
     */
    private static final ResourceProvider MODEL_PROVIDER = new ResourceProvider() {

        @Override
        public String resourceType() {
            return ML_MODEL_RESOURCE_TYPE;
        }

        @Override
        public String resourceIndexName() {
            return ML_MODEL_INDEX;
        }

        /**
         * Model chunks share {@link org.opensearch.ml.common.CommonValue#ML_MODEL_INDEX} with model metadata
         * documents and are not resources. Only metadata documents carry {@code resource_type}, so a chunk write
         * resolves to no provider and is skipped. The framework returns the extracted field value as the resource
         * type, so the stamped value has to be the type identifier itself, not a discriminator of our choosing.
         */
        @Override
        public String typeField() {
            return RESOURCE_TYPE_FIELD;
        }

        @Override
        public String parentType() {
            return ML_MODEL_GROUP_RESOURCE_TYPE;
        }

        @Override
        public String parentIdField() {
            return MODEL_GROUP_ID_FIELD;
        }

        /**
         * Migration attribution reads the owner from the document. {@code MLModel} declares a {@code user} field and
         * the index maps it, but registration does not populate it yet, so today this resolves to null and migration
         * falls back to the group's record. Declaring the path now means attribution starts working as soon as
         * registration stamps the field.
         */
        @Override
        public String ownerNamePath() {
            return "/user/name";
        }

        @Override
        public String ownerBackendRolesPath() {
            return "/user/backend_roles";
        }

        /** The model index maps no workspaces field, so opt out rather than have the write path look for one. */
        @Override
        public String workspacesField() {
            return null;
        }
    };

    /**
     * Standalone connectors are a simpler case than models: one document per resource in its own index, with {@code
     * owner} and {@code backend_roles} already on the document, so there is nothing to discriminate and no parent to
     * inherit from. The owner paths let the security plugin's migrate endpoint attribute existing connectors without
     * any of the extra capabilities model migration needs.
     */
    private static final ResourceProvider CONNECTOR_PROVIDER = new ResourceProvider() {

        @Override
        public String resourceType() {
            return ML_CONNECTOR_RESOURCE_TYPE;
        }

        @Override
        public String resourceIndexName() {
            return ML_CONNECTOR_INDEX;
        }

        @Override
        public String ownerNamePath() {
            return "/owner/name";
        }

        @Override
        public String ownerBackendRolesPath() {
            return "/owner/backend_roles";
        }

        /** The connector index maps no workspaces field, so opt out rather than have the write path look for one. */
        @Override
        public String workspacesField() {
            return null;
        }
    };

    @Override
    public Set<ResourceProvider> getResourceProviders() {
        return Set.of(MODEL_GROUP_PROVIDER, MODEL_PROVIDER, CONNECTOR_PROVIDER);
    }

    @Override
    public void assignResourceSharingClient(ResourceSharingClient resourceSharingClient) {
        ResourceSharingClientAccessor.getInstance().setResourceSharingClient(resourceSharingClient);
    }
}
