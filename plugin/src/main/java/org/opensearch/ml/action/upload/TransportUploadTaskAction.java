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

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.ml.common.transport.upload.UploadTaskAction;
import org.opensearch.ml.common.transport.upload.UploadTaskRequest;
import org.opensearch.ml.common.transport.upload.UploadTaskResponse;
import org.opensearch.ml.task.UploadTaskRunner;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

@Log4j2
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class TransportUploadTaskAction extends HandledTransportAction<ActionRequest, UploadTaskResponse> {
    UploadTaskRunner uploadTaskRunner;
    TransportService transportService;

    @Inject
    public TransportUploadTaskAction(TransportService transportService, ActionFilters actionFilters, UploadTaskRunner uploadTaskRunner) {
        super(UploadTaskAction.NAME, transportService, actionFilters, UploadTaskRequest::new);
        this.uploadTaskRunner = uploadTaskRunner;
        this.transportService = transportService;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<UploadTaskResponse> listener) {
        UploadTaskRequest uploadTaskRequest = UploadTaskRequest.fromActionRequest(request);
        uploadTaskRunner.runUpload(uploadTaskRequest, transportService, listener);
    }
}
