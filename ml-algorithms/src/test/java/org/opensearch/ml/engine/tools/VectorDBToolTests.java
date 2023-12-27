package org.opensearch.ml.engine.tools;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.client.Client;
import org.opensearch.core.xcontent.NamedXContentRegistry;

public class VectorDBToolTests {
    @Mock
    private Client client;

    @Mock
    private NamedXContentRegistry contentRegistry;

    private VectorDBTool vectorDBTool;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        VectorDBTool.Factory.getInstance().init(client, contentRegistry);
        Map<String, Object> params = new HashMap<>();
        params.put(AbstractRetrieverTool.INDEX_FIELD, "index_name");
        params.put(VectorDBTool.EMBEDDING_FIELD, "embedding_name");
        params.put(VectorDBTool.MODEL_ID_FIELD, "id_field");
        params.put(AbstractRetrieverTool.SOURCE_FIELD, "['source_field_1', 'source_field_2']");
        params.put(AbstractRetrieverTool.DOC_SIZE_FIELD, "1");
        vectorDBTool = VectorDBTool.Factory.getInstance().create(params);
    }

    @Test
    public void test_getQueryBody_GeneratesQuery() {
        String query = vectorDBTool.getQueryBody("query_text");

        Assert
            .assertEquals(
                "{\"query\":{\"neural\":{\"embedding_name\":{\"query_text\":\"query_text\",\"model_id\":\"id_field\",\"k\":10}}} }",
                query
            );
    }

    @Test
    public void test_getQueryBody_DefaultDocSizeField() {
        Map<String, Object> params = new HashMap<>();
        params.put(AbstractRetrieverTool.INDEX_FIELD, "index_name");
        params.put(VectorDBTool.EMBEDDING_FIELD, "embedding_name");
        params.put(VectorDBTool.MODEL_ID_FIELD, "id_field");
        params.put(AbstractRetrieverTool.SOURCE_FIELD, "['source_field_1', 'source_field_2']");
        vectorDBTool = VectorDBTool.Factory.getInstance().create(params);

        Assert.assertEquals((Integer) 2, vectorDBTool.getDocSize());
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_getQueryBody_EmptyEmbeddingField_ThrowsException() {
        Map<String, Object> params = new HashMap<>();
        params.put(AbstractRetrieverTool.INDEX_FIELD, "index_name");
        params.put(VectorDBTool.MODEL_ID_FIELD, "id_field");
        params.put(AbstractRetrieverTool.SOURCE_FIELD, "['source_field_1', 'source_field_2']");
        params.put(AbstractRetrieverTool.DOC_SIZE_FIELD, "1");
        vectorDBTool = VectorDBTool.Factory.getInstance().create(params);

        vectorDBTool.getQueryBody("query_text");
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_getQueryBody_EmptyModelField_ThrowsException() {
        Map<String, Object> params = new HashMap<>();
        params.put(VectorDBTool.EMBEDDING_FIELD, "embedding_name");
        params.put(AbstractRetrieverTool.INDEX_FIELD, "index_name");
        params.put(AbstractRetrieverTool.SOURCE_FIELD, "['source_field_1', 'source_field_2']");
        params.put(AbstractRetrieverTool.DOC_SIZE_FIELD, "1");
        vectorDBTool = VectorDBTool.Factory.getInstance().create(params);

        vectorDBTool.getQueryBody("query_text");
    }
}
