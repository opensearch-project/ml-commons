package org.opensearch.searchpipelines.questionanswering.generative;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.Client;
import org.opensearch.search.pipeline.SearchRequestProcessor;
import org.opensearch.test.OpenSearchTestCase;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;

public class GenerativeQARequestProcessorTests extends OpenSearchTestCase {

    public void testProcessorFactory() throws Exception {

        Map<String, Object> config = new HashMap<>();
        config.put("model_id", "foo");
        SearchRequestProcessor processor =
            new GenerativeQARequestProcessor.Factory().create(null, "tag", "desc", true, config, null);
        assertTrue(processor instanceof GenerativeQARequestProcessor);
    }

    public void testProcessRequest() throws Exception {
        GenerativeQARequestProcessor processor = new GenerativeQARequestProcessor("tag", "desc", false, "foo");
        SearchRequest request = new SearchRequest();
        SearchRequest processed = processor.processRequest(request);
        assertEquals(request, processed);
    }
}
