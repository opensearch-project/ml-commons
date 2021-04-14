/*
 *  Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License").
 *  You may not use this file except in compliance with the License.
 *  A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */


package org.opensearch.ml.action.stats;

import org.opensearch.action.support.nodes.BaseNodeRequest;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;

import java.io.IOException;

public class MLStatsNodeRequest extends BaseNodeRequest {
    private MLStatsNodesRequest request;

    /**
     * Constructor
     */
    public MLStatsNodeRequest() {
        super();
    }

    public MLStatsNodeRequest(StreamInput in) throws IOException {
        super(in);
        this.request = new MLStatsNodesRequest(in);
    }

    /**
     * Constructor
     *
     * @param request MLStatsNodesRequest
     */
    public MLStatsNodeRequest(MLStatsNodesRequest request) {
        this.request = request;
    }

    /**
     * Get MLStatsRequest
     *
     * @return MLStatsNodesRequest for this node
     */
    public MLStatsNodesRequest getMLStatsNodesRequest() {
        return request;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        request.writeTo(out);
    }
}
