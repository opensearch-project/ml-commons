/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.oauth2;

import org.opensearch.core.action.ActionListener;

/** Supplies the access token for requests of one connector. */
public interface AuthorizationProvider {
    /** Obtains a token for one request, from the node's cache or the token endpoint. May complete on another thread. */
    void authorizationValue(ActionListener<TokenHandle> listener);

    /**
     * The remote service answered 401 to a request that carried {@code handle}'s token. The token is dropped unless a
     * newer one replaced it or it is younger than ten seconds, which bounds the token requests a remote service that
     * rejects every token can cause.
     *
     * @return whether the request may be sent once more with another token; false only when the token is kept
     */
    boolean invalidate(TokenHandle handle);
}
