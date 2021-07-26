/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.common.transport.upload;

import org.opensearch.action.ActionType;

public class UploadTaskAction extends ActionType<UploadTaskResponse> {
    public static final UploadTaskAction INSTANCE = new UploadTaskAction();
    public static final String NAME = "cluster:admin/opensearch-ml/upload";

    private UploadTaskAction() {
        super(NAME, UploadTaskResponse::new);
    }
}
