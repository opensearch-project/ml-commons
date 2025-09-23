package org.opensearch.ml.engine.algorithms.remote;

import java.io.IOException;

import org.opensearch.core.action.ActionListener;
import org.opensearch.core.transport.TransportResponse;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.TransportRequest;

public class StreamPredictActionListener<Response extends TransportResponse, Request extends TransportRequest>
    implements
        ActionListener<Response> {

    private final TransportChannel channel;

    public StreamPredictActionListener(TransportChannel channel) {
        this.channel = channel;
    }

    /**
     * Send streaming responses
     * This allows multiple responses to be sent for a single request.
     *
     * @param response    the intermediate response to send
     * @param isLastBatch whether this response is the last one
     */
    public void onStreamResponse(Response response, boolean isLastBatch) {
        assert response != null;
        channel.sendResponseBatch(response);
        if (isLastBatch) {
            channel.completeStream();
        }
    }

    /**
     * Reuse ActionListener method to send the last stream response
     * This maintains compatibility on data node side
     *
     * @param response the response to send
     */
    @Override
    public final void onResponse(Response response) {
        onStreamResponse(response, true);
    }

    @Override
    public void onFailure(Exception e) {
        try {
            channel.sendResponse(e);
        } catch (IOException exc) {
            channel.completeStream();
            throw new RuntimeException(exc);
        }
    }
}
