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
