/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.helper;

import org.opensearch.commons.authuser.User;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MemoryAccessControlHelper {
    public boolean checkMemoryContainerAccess(User user, MLMemoryContainer mlMemoryContainer) {
        // If security is disabled (user is null), allow access
        if (user == null) {
            return true;
        }

        // If user is admin (has all_access role), allow access
        if (user.getRoles() != null && user.getRoles().contains("all_access")) {
            return true;
        }

        // Check if user is the owner
        User owner = mlMemoryContainer.getOwner();
        if (owner != null && owner.getName() != null && owner.getName().equals(user.getName())) {
            return true;
        }

        // Check if user has matching backend roles
        if (owner != null && owner.getBackendRoles() != null && user.getBackendRoles() != null) {
            return owner.getBackendRoles().stream().anyMatch(role -> user.getBackendRoles().contains(role));
        }

        return false;
    }
}
