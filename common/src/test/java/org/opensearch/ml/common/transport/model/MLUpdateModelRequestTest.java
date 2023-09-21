package org.opensearch.ml.common.transport.model;

import org.junit.Before;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.HttpConnectorTest;
import org.opensearch.ml.common.connector.MLPostProcessFunction;
import org.opensearch.ml.common.connector.MLPreProcessFunction;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MLUpdateModelRequestTest {

    private MLUpdateModelInput updateModelInput;
    private TextEmbeddingModelConfig config;
    private Map<String, Object> connector;

    @Before
    public void setUp(){
        connector = Map.of("test-connector-key","test-connector-value");

        MLModelConfig config = TextEmbeddingModelConfig.builder()
                .modelType("testModelType")
                .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
                .frameworkType(TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
                .embeddingDimension(100)
                .build();

        updateModelInput.toBuilder()
                .modelGroupId("modelGroupId")
                .name("name")
                .description("description")
                .modelConfig(config)
                .connector(connector)
                .connectorId("test-connector_id");
    }
}
