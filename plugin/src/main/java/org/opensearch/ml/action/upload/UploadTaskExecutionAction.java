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

package org.opensearch.ml.action.upload;

import org.opensearch.action.ActionType;
import org.opensearch.ml.common.transport.upload.UploadTaskResponse;

public class UploadTaskExecutionAction extends ActionType<UploadTaskResponse> {
    public static UploadTaskExecutionAction INSTANCE = new UploadTaskExecutionAction();
    public static final String NAME = "cluster:admin/opensearch-ml/upload/execution";

    public UploadTaskExecutionAction() {
        super(NAME, UploadTaskResponse::new);
    }
}
