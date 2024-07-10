package org.opensearch.ml.engine.ingest;

import org.opensearch.client.Client;
import org.opensearch.ml.common.transport.batch.MLBatchIngestionInput;
import org.opensearch.ml.engine.annotation.Ingester;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Ingester("openai")
public class openAIDataIngestion implements Ingestable {
    public static final String SOURCE = "source";
    private final Client client;

    public openAIDataIngestion(Client client) {
        this.client = client;
    }

    @Override
    public double ingest(MLBatchIngestionInput mlBatchIngestionInput) {
        double successRate = 0;

        return successRate;
    }
}
