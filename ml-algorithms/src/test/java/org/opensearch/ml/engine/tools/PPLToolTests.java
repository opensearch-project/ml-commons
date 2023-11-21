package org.opensearch.ml.engine.tools;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.client.AdminClient;
import org.opensearch.client.Client;
import org.opensearch.client.IndicesAdminClient;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.ml.common.spi.tools.Tool;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class PPLToolTests {
    @Mock
    private Client client;
    @Mock
    private AdminClient adminClient;
    @Mock
    private IndicesAdminClient indicesAdminClient;

    @Mock
    private MappingMetadata mappingMetadata;

    private Map<String, MappingMetadata> mockedMappings;

    private Map<String, Object> mappings;

    private String mockedIndexName = "demo";
    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        when(mappingMetadata.getSourceAsMap()).thenReturn();
        when(adminClient.indices()).thenReturn(indicesAdminClient);
        when(client.admin()).thenReturn(adminClient);

        when(indexMetadata.getState()).thenReturn(IndexMetadata.State.OPEN);
        when(indexMetadata.getCreationVersion()).thenReturn(Version.CURRENT);

        when(metadata.index(any(String.class))).thenReturn(indexMetadata);
        when(clusterState.metadata()).thenReturn(metadata);
        when(clusterService.state()).thenReturn(clusterState);

        CatIndexTool.Factory.getInstance().init(client, clusterService);

        indicesParams = Map.of("index", "[\"foo\"]");
        otherParams = Map.of("other", "[\"bar\"]");
        emptyParams = Collections.emptyMap();
    }

    @Test
    public void testTool() {
        Tool tool = CatIndexTool.Factory.getInstance().create(Collections.emptyMap());
        assertEquals(CatIndexTool.TYPE, tool.getName());
        assertTrue(tool.validate(indicesParams));
        assertTrue(tool.validate(otherParams));
        assertFalse(tool.validate(emptyParams));
    }

    private void createMockedMappings()
    {
        mappings = new HashMap<>();
        mappings.put("demoFields", (Object) Collections.)
        mockedMappings = new HashMap<>();
        MappingMetadata

    }
}
