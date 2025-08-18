/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.requests.list;

import java.io.IOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;

public class MLMcpToolsListRequest extends ActionRequest {

    public MLMcpToolsListRequest(StreamInput input) throws IOException {
        super(input);
    }

    public MLMcpToolsListRequest() {
        super();
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }
}
