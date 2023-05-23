/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.helper;

import org.opensearch.action.ActionListener;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.commons.authuser.User;
import org.opensearch.ml.utils.SecurityUtils;

import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_VALIDATE_BACKEND_ROLES;

public class ModelAccessControlHelper {

    private volatile boolean modelAccessControlEnabled;
    public ModelAccessControlHelper(ClusterService clusterService, Settings settings) {
        modelAccessControlEnabled = ML_COMMONS_VALIDATE_BACKEND_ROLES.get(settings);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_VALIDATE_BACKEND_ROLES, it -> modelAccessControlEnabled = it);
    }

    public boolean skipModelAccessControl(User user) {
        return user == null || !modelAccessControlEnabled || SecurityUtils.isAdmin(user);
    }

    public boolean isSecurityEnabledAndModelAccessControlEnabled(User user) {
        return user != null && modelAccessControlEnabled;
    }

    public void validateModelGroupAccess(User user, String modelGroupId, Client client, ActionListener<Boolean> listener) {
        SecurityUtils.validateModelGroupAccess(user, modelGroupId, client, modelAccessControlEnabled, listener);
    }

}
