/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

public enum MLModelState {
    TRAINED,
    @Deprecated
    UPLOADING,
    @Deprecated
    UPLOADED,
    @Deprecated
    LOADING,
    @Deprecated
    LOADED,
    @Deprecated
    PARTIALLY_LOADED,
    @Deprecated
    UNLOADED,
    @Deprecated
    LOAD_FAILED,

    REGISTERING,
    REGISTERED,
    DEPLOYING,
    DEPLOYED,
    PARTIALLY_DEPLOYED,
    UNDEPLOYED,
    DEPLOY_FAILED;

    public static MLModelState from(String value) {
        try {
            return MLModelState.valueOf(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Wrong model state");
        }
    }
}
