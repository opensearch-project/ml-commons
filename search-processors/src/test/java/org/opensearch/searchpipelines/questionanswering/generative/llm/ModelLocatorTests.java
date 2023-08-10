package org.opensearch.searchpipelines.questionanswering.generative.llm;

import org.opensearch.client.Client;
import org.opensearch.test.OpenSearchTestCase;

import static org.mockito.Mockito.mock;

public class ModelLocatorTests extends OpenSearchTestCase {

    public void testGetRemoteLlm() {
        Client client = mock(Client.class);
        Llm llm = ModelLocator.getRemoteLlm("xyz", client);
        assertTrue(llm instanceof OpenSearchChatConnector);
    }
}
