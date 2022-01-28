/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.permission;

import lombok.extern.log4j.Log4j2;

import org.opensearch.client.Client;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;

/**
 * AccessController has common code for backend roles based permission check.
 */
@Log4j2
public class AccessController {
    public static String getUserStr(Client client) {
        return client.threadPool().getThreadContext().getTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT);
    }

    public static User getUserContext(Client client) {
        return User.parse(getUserStr(client));
    }

    public static boolean checkUserPermissions(User requestedUser, User resourceUser, String modelId) {
        if (requestedUser == null || resourceUser == null) {
            // requestUser would be null if Security is disabled or request user is super admin
            // resourceUser is null means this model doesn't have user assigned with.
            return true;
        }

        if (resourceUser.getBackendRoles() == null || requestedUser.getBackendRoles() == null) {
            // return false if backend roles mismatch.
            return false;
        }

        // Check if requested user has backend role required to access the resource
        for (String backendRole : requestedUser.getBackendRoles()) {
            if (resourceUser.getBackendRoles().contains(backendRole)) {
                log
                    .debug(
                        "User: "
                            + requestedUser.getName()
                            + " has backend role: "
                            + backendRole
                            + " permissions to access model: "
                            + modelId
                    );
                return true;
            }
        }
        return false;
    }
}
