package org.opensearch.ml.engine.tools;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.AdminClient;
import org.opensearch.client.Client;
import org.opensearch.client.IndicesAdminClient;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.spi.tools.Tool;
import org.json.JSONObject;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import software.amazon.awssdk.utils.ImmutableMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

public class PPLToolTests {
    @Mock
    private Client client;
    @Mock
    private AdminClient adminClient;
    @Mock
    private IndicesAdminClient indicesAdminClient;

    @Mock
    private GetMappingsResponse getMappingsResponse;

    @Mock
    private MappingMetadata mappingMetadata;

    private Map<String, MappingMetadata> mockedMappings;
    private Map<String, Object> indexMappings;
    @Mock
    private SearchHits searchHits;

    @Mock
    private SearchHit hit;

    private Map<String, Object> sampleMapping;


    @Mock
    private SearchResponse searchResponse;


    @Mock
    private MLTaskResponse mlTaskResponse;


    private String mockedIndexName = "demo";
    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        createMappings();
        //get mapping
        when(mappingMetadata.getSourceAsMap()).thenReturn(indexMappings);
        when(getMappingsResponse.getMappings()).thenReturn(mockedMappings);
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesAdminClient);
        doAnswer(invocation -> {
            ActionListener<GetMappingsResponse> listener = (ActionListener<GetMappingsResponse>) invocation.getArguments()[1];
            listener.onResponse(getMappingsResponse);
            return null;
        }).when(indicesAdminClient).getMappings(any(), any());
        //mockedMappings (index name, mappingmetadata)



        //search result
        when(hit.getSourceAsMap()).thenReturn(sampleMapping);
        when(searchHits.getHits()).thenReturn(new SearchHit[] {hit});
        when(searchResponse.getHits()).thenReturn(searchHits);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = (ActionListener<SearchResponse>) invocation.getArguments()[1];
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());



        // call model
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = (ActionListener<MLTaskResponse>) invocation.getArguments()[2];
            listener.onResponse(mlTaskResponse);
            return null;
        }).when(client).execute(MLPredictionTaskAction.INSTANCE, any(), any());
    }

    @Test
    public void testTool() {
        Tool tool = PPLTool.Factory.getInstance().create(Collections.emptyMap());
        assertEquals(PPLTool.TYPE, tool.getName());

    }

    private void createMappings()
    {
        indexMappings = new HashMap<>();
        indexMappings.put("demoFields", ImmutableMap.of("type", "text"));
        mockedMappings = new HashMap<>();
        mockedMappings.put(mockedIndexName, mappingMetadata);


    }
}
