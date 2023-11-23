package org.opensearch.ml.engine.tools;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.mockito.Mockito;

import java.util.Map;

public class AbstractRetrieverToolTests {
    static protected String  MOCKED_QUERY = "mock query";

    @Test
    public void testDemo() throws Exception {
        AbstractRetrieverTool mockedImpl = Mockito.mock(AbstractRetrieverTool.class, Mockito.CALLS_REAL_METHODS);
        when(mockedImpl.getQueryBody(any(String.class))).thenReturn(MOCKED_QUERY);
        mockedImpl.run(Map.of());
    }
}
