package org.opensearch.ml.engine.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import lombok.SneakyThrows;

public class NeuralSparseToolTests {
    public static final String TEST_EMBEDDING_FIELD = "test embedding";
    public static final String TEST_MODEL_ID = "123fsd23134";
    private Map<String, Object> params = new HashMap<>();

    @Before
    public void setup() {
        params.put(NeuralSparseTool.INDEX_FIELD, AbstractRetrieverToolTests.TEST_INDEX);
        params.put(NeuralSparseTool.EMBEDDING_FIELD, TEST_EMBEDDING_FIELD);
        params.put(NeuralSparseTool.SOURCE_FIELD, gson.toJson(AbstractRetrieverToolTests.TEST_SOURCE_FIELDS));
        params.put(NeuralSparseTool.MODEL_ID_FIELD, TEST_MODEL_ID);
        params.put(NeuralSparseTool.DOC_SIZE_FIELD, AbstractRetrieverToolTests.TEST_DOC_SIZE.toString());
    }

    @Test
    @SneakyThrows
    public void testCreateTool() {
        NeuralSparseTool tool = NeuralSparseTool.Factory.getInstance().create(params);
        assertEquals(AbstractRetrieverToolTests.TEST_INDEX, tool.getIndex());
        assertEquals(TEST_EMBEDDING_FIELD, tool.getEmbeddingField());
        assertEquals(AbstractRetrieverToolTests.TEST_SOURCE_FIELDS, tool.getSourceFields());
        assertEquals(TEST_MODEL_ID, tool.getModelId());
        assertEquals(AbstractRetrieverToolTests.TEST_DOC_SIZE, tool.getDocSize());
        assertEquals("NeuralSparseTool", tool.getType());
        assertEquals("NeuralSparseTool", tool.getName());
        assertEquals("Use this tool to search data in OpenSearch index.", NeuralSparseTool.Factory.getInstance().getDefaultDescription());
    }

    @Test
    @SneakyThrows
    public void testGetQueryBody() {
        NeuralSparseTool tool = NeuralSparseTool.Factory.getInstance().create(params);
        assertEquals(
            "{\"query\":{\"neural_sparse\":{\"test embedding\":{\""
                + "query_text\":\"{\"query\":{\"match_all\":{}}}\",\"model_id\":\"123fsd23134\"}}} }",
            tool.getQueryBody(AbstractRetrieverToolTests.TEST_QUERY)
        );
    }

    @Test
    @SneakyThrows
    public void testGetQueryBodyWithIllegalParams() {
        Map<String, Object> illegalParams1 = new HashMap<>(params);
        illegalParams1.remove(NeuralSparseTool.MODEL_ID_FIELD);
        NeuralSparseTool tool1 = NeuralSparseTool.Factory.getInstance().create(illegalParams1);
        assertThrows(
            "Parameter [embedding_field] and [model_id] can not be null or empty.",
            IllegalArgumentException.class,
            () -> tool1.getQueryBody(AbstractRetrieverToolTests.TEST_QUERY)
        );

        Map<String, Object> illegalParams2 = new HashMap<>(params);
        illegalParams1.remove(NeuralSparseTool.EMBEDDING_FIELD);
        NeuralSparseTool tool2 = NeuralSparseTool.Factory.getInstance().create(illegalParams1);
        assertThrows(
            "Parameter [embedding_field] and [model_id] can not be null or empty.",
            IllegalArgumentException.class,
            () -> tool2.getQueryBody(AbstractRetrieverToolTests.TEST_QUERY)
        );
    }
}
