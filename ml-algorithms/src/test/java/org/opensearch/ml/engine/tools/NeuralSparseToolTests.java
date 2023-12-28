package org.opensearch.ml.engine.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.opensearch.client.Client;
import org.opensearch.core.xcontent.NamedXContentRegistry;

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
        NeuralSparseTool.Factory.getInstance().init(Mockito.mock(Client.class), Mockito.mock(NamedXContentRegistry.class));
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
    public void testInstanceCreationWithIllegalParams() {
        Map<String, Object> illegalParams1 = new HashMap<>(params);
        illegalParams1.remove(NeuralSparseTool.MODEL_ID_FIELD);
        assertThrows(
            "Parameter [model_id] can not be null.",
            IllegalArgumentException.class,
            () -> NeuralSparseTool.Factory.getInstance().create(illegalParams1)
        );

        Map<String, Object> illegalParams2 = new HashMap<>(params);
        illegalParams2.remove(NeuralSparseTool.EMBEDDING_FIELD);
        assertThrows(
            "Parameter [embedding_id] can not be null.",
            IllegalArgumentException.class,
            () -> NeuralSparseTool.Factory.getInstance().create(illegalParams2)
        );

        Map<String, Object> illegalParams3 = new HashMap<>(params);
        illegalParams3.remove(NeuralSparseTool.SOURCE_FIELD);
        assertThrows(
            "Parameter [source_field] can not be null.",
            IllegalArgumentException.class,
            () -> NeuralSparseTool.Factory.getInstance().create(illegalParams3)
        );

        Map<String, Object> illegalParams4 = new HashMap<>(params);
        illegalParams4.remove(NeuralSparseTool.INDEX_FIELD);
        assertThrows(
            "Parameter [index_field] can not be null.",
            IllegalArgumentException.class,
            () -> NeuralSparseTool.Factory.getInstance().create(illegalParams4)
        );
    }

    @Test
    @SneakyThrows
    public void testInstanceCreationInvalidSourceField() {
        Map<String, Object> illegalParams = new HashMap<>(params);
        illegalParams.put(NeuralSparseTool.SOURCE_FIELD, "invalid");
        assertThrows(
            "Parameter [source_field] needs to be a json array.",
            IllegalArgumentException.class,
            () -> NeuralSparseTool.Factory.getInstance().create(illegalParams)
        );
    }
}
