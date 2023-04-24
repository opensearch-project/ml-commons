/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.get.GetRequest;
import org.opensearch.client.Client;
import org.opensearch.commons.authuser.User;
import org.opensearch.ml.common.MLModelGroup;

@Log4j2
public class SecurityUtils {

    public static boolean validateModelGroupAccess(User user, String modelGroupId, Client client) {
        List<String> userBackendRoles = user.getBackendRoles();
        log.info(userBackendRoles);

        GetRequest getModelGroupRequest = new GetRequest(ML_MODEL_GROUP_INDEX).id(modelGroupId);
        log.info(modelGroupId);
        List<String> modelGroupBackendRoles = new ArrayList<>();
        try {
            Map<String, Object> source = client.get(getModelGroupRequest).get().getSourceAsMap();
            log.info(getModelGroupRequest);
            modelGroupBackendRoles = (List<String>) source.get(MLModelGroup.BACKEND_ROLES_FIELD);
            log.info("try block succeeded");
        } catch (Exception e) {
            log.error("Failed to get model group", e);
            throw new IllegalArgumentException("Failed to get model group", e);
        }
        return userBackendRoles.stream().anyMatch(modelGroupBackendRoles.stream().collect(Collectors.toSet())::contains);
    }

    public static boolean isAdmin(User user) {
        if (user == null) {
            return false;
        }
        if (user.getRoles().isEmpty()) {
            return false;
        }
        return user.getRoles().contains("all_access");
    }
}
