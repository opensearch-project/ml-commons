/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.prompt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_PROMPT_INDEX;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.prompt.MLPrompt;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

public class MLPromptManagerTests extends OpenSearchTestCase {
    private static final String PROMPT_ID = "prompt_id";

    private final String expectedInputStrWithMessages = "[{\"role\": \"system\", \"content\": \"pull_prompt(prompt_id).system\"},"
        + "{\"role\": \"user\", \"content\": \"pull_prompt(prompt_id).user\"}]";

    private final String expectedInputStrWithPrompt = "pull_prompt(prompt_id).user";

    private final String expectedInputStrWithPromptParameters = "{\"prompt_id\": {\"variable\": \"test\"}}";

    @Mock
    private Client client;
    private SdkClient sdkClient;

    @Mock
    ThreadPool threadPool;

    ThreadContext threadContext;

    @Mock
    ActionListener<MLPrompt> getPromptActionListener;

    @Mock
    ActionListener<GetResponse> getResponseActionListener;

    @Mock
    ActionListener<Map<String, String>> getInputParameterListener;

    @Mock
    ActionListener<SearchResponse> searchResponseActionListener;

    private MLPromptManager mlPromptManager;

    @Mock
    private GetResponse getResponse;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);

        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());
        threadContext = new ThreadContext(Settings.EMPTY);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        mlPromptManager = spy(new MLPromptManager(client, sdkClient));

        getResponse = createGetResponse("test prompt", "test prompt");
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        SearchResponse searchResponse = createSearchResponse(0);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());
    }

    @Test
    public void testConstructor() {
        MLPromptManager mlPromptManager = new MLPromptManager(client, sdkClient);
        assertNotNull(mlPromptManager);
    }

    @Test
    public void testGetPrompt_success() throws IOException {
        GetDataObjectRequest getDataObjectRequest = GetDataObjectRequest.builder().index(ML_PROMPT_INDEX).id(PROMPT_ID).build();
        mlPromptManager.getPromptAsync(getDataObjectRequest, PROMPT_ID, getPromptActionListener);

        ArgumentCaptor<MLPrompt> argumentCaptor = ArgumentCaptor.forClass(MLPrompt.class);
        verify(getPromptActionListener).onResponse(argumentCaptor.capture());

        MLPrompt capturedPrompt = argumentCaptor.getValue();
        assertNotNull(capturedPrompt);
        assertEquals("test prompt", capturedPrompt.getName());
    }

    @Test
    public void testGetPromptException() {
        GetDataObjectRequest getDataObjectRequest = GetDataObjectRequest.builder().index(ML_PROMPT_INDEX).id(PROMPT_ID).build();

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Failed to get prompt"));
            return null;
        }).when(client).get(any(), any());

        mlPromptManager.getPromptAsync(getDataObjectRequest, PROMPT_ID, getPromptActionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getPromptActionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to get data object from index .plugins-ml-prompt", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testValidateTagsWithExceededTagsSize() {
        List<String> tags = Collections.nCopies(MLPromptManager.MAX_NUMBER_OF_TAGS + 1, "tag");
        boolean result = MLPromptManager.validateTags(tags);

        assertFalse(result);
    }

    @Test
    public void testValidateTagsWithExceededTagCharacterSize() {
        List<String> tags = Collections.singletonList("a".repeat(MLPromptManager.MAX_LENGTH_OF_TAG + 1));
        boolean result = MLPromptManager.validateTags(tags);

        assertFalse(result);
    }

    @Test
    public void testHandleFailureWithIndexNotFoundException() {
        MLPromptManager
            .handleFailure(
                new IndexNotFoundException("Failed to get data object from index .plugins-ml-prompt"),
                "prompt_id",
                getResponseActionListener,
                "Failed to get prompt index"
            );
        ArgumentCaptor<OpenSearchStatusException> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(getResponseActionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find prompt with the provided prompt id: prompt_id", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testHandleFailureWithException() {
        GetResponse getResponse = createGetResponse("test prompt", "test prompt");
        MLPromptManager
            .handleFailure(new Exception("Fail to get prompt"), "prompt_id", getResponseActionListener, "Failed to get a prompt");
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getResponseActionListener).onFailure(argumentCaptor.capture());
        assertEquals("Fail to get prompt", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testBuildInputParametersWithMessagesSuccess() throws IOException {
        getResponse = createGetResponse("This is ${prompt_parameters.variable}", "test system prompt");
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        Map<String, String> inputParameters = Map
            .of("messages", expectedInputStrWithMessages, "prompt_parameters", expectedInputStrWithPromptParameters);
        mlPromptManager.buildInputParameters("messages", inputParameters, "tenant_id", getInputParameterListener);
        String expectedOutputStr =
            "[{\"role\":\"system\",\"content\":\"test system prompt\"},{\"role\":\"user\",\"content\":\"This is test\"}]";

        ArgumentCaptor<Map<String, String>> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(getInputParameterListener).onResponse(argumentCaptor.capture());
        Map<String, String> capturedInputParameter = argumentCaptor.getValue();
        assertEquals(expectedOutputStr, capturedInputParameter.get("messages"));
    }

    @Test
    public void testBuildInputParametersWithPromptSuccess() throws IOException {
        getResponse = createGetResponse("This is ${prompt_parameters.variable}", "test system prompt");
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        Map<String, String> inputParameters = Map
            .of("prompt", expectedInputStrWithPrompt, "prompt_parameters", expectedInputStrWithPromptParameters);
        mlPromptManager.buildInputParameters("prompt", inputParameters, "tenant_id", getInputParameterListener);
        String expectedOutputStr = "This is test";

        ArgumentCaptor<Map<String, String>> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(getInputParameterListener).onResponse(argumentCaptor.capture());
        Map<String, String> capturedInputParameter = argumentCaptor.getValue();
        assertEquals(expectedOutputStr, capturedInputParameter.get("prompt"));
    }

    @Test
    public void testBuildInputParametersFailWithWrongPromptTypeInput() {
        String IncorrectPromptType = "wrongPrompt";

        Map<String, String> inputParameters = Map
            .of(IncorrectPromptType, expectedInputStrWithPrompt, "prompt_parameters", expectedInputStrWithPromptParameters);
        mlPromptManager.buildInputParameters(IncorrectPromptType, inputParameters, "tenant_id", getInputParameterListener);

        ArgumentCaptor<IllegalArgumentException> argumentCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(getInputParameterListener).onFailure(argumentCaptor.capture());
        IllegalArgumentException exception = argumentCaptor.getValue();
        assertEquals("Wrong prompt type is provided: wrongPrompt, should provide either prompt or messages", exception.getMessage());
    }

    @Test
    public void testBuildInputParametersFailWithKeyMissMatching() {
        String exampleInput = "[{\"role\": \"system\", \"content\": \"pull_prompt(prompt_id).user\"},"
            + "{\"role\": \"user\", \"content\": \"pull_prompt(prompt_id).user\"}]";

        Map<String, String> inputParameters = Map.of("messages", exampleInput, "prompt_parameters", expectedInputStrWithPromptParameters);
        mlPromptManager.buildInputParameters("messages", inputParameters, "tenant_id", getInputParameterListener);

        ArgumentCaptor<IllegalArgumentException> argumentCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(getInputParameterListener).onFailure(argumentCaptor.capture());
        IllegalArgumentException exception = argumentCaptor.getValue();
        assertEquals("Specified key does not match the provided role", exception.getMessage());
    }

    @Test
    public void testBuildInputParametersFailWithKeyNotExist() throws IOException {
        getResponse = createGetResponse("This is ${prompt_parameters.variable}", "test system prompt");
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        String exampleInput = "[{\"role\": \"system\", \"content\": \"pull_prompt(prompt_id).system\"},"
            + "{\"role\": \"user\", \"content\": \"pull_prompt(prompt_id).user\"},"
            + "{\"role\": \"developer\", \"content\": \"pull_prompt(prompt_id).developer\"}]";

        Map<String, String> inputParameters = Map.of("messages", exampleInput, "prompt_parameters", expectedInputStrWithPromptParameters);
        mlPromptManager.buildInputParameters("messages", inputParameters, "tenant_id", getInputParameterListener);

        ArgumentCaptor<OpenSearchStatusException> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(getInputParameterListener).onFailure(argumentCaptor.capture());
        OpenSearchStatusException exception = argumentCaptor.getValue();
        assertEquals(
            "Failed to process prompt: prompt_id for following reason: Content for specified key is not defined in ML Prompt",
            exception.getMessage()
        );
    }

    @Test
    public void testBuildInputParametersFailWithRemainingPlaceholderVariables() throws IOException {
        getResponse = createGetResponse("This is ${prompt_parameters.variable}", "test system prompt");
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        String examplePromptParameters = "{\"prompt_id\": {\"unknownVariable\": \"test\"}}";
        Map<String, String> inputParameters = Map.of("prompt", expectedInputStrWithPrompt, "prompt_parameters", examplePromptParameters);
        mlPromptManager.buildInputParameters("prompt", inputParameters, "tenant_id", getInputParameterListener);

        ArgumentCaptor<OpenSearchStatusException> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(getInputParameterListener).onFailure(argumentCaptor.capture());
        OpenSearchStatusException exception = argumentCaptor.getValue();
        assertEquals(
            "Failed to process prompt: prompt_id for following reason: Failed to replace all the placeholders",
            exception.getMessage()
        );
    }

    @Test
    public void testBuildInputParametersFailWithPromptNotFound() throws IOException {
        getResponse = createGetResponse("This is ${prompt_parameters.variable}", "test system prompt");
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new IndexNotFoundException("Failed to find ML Prompt"));
            return null;
        }).when(client).get(any(), any());

        Map<String, String> inputParameters = Map
            .of("prompt", expectedInputStrWithPrompt, "prompt_parameters", expectedInputStrWithPromptParameters);
        mlPromptManager.buildInputParameters("prompt", inputParameters, "tenant_id", getInputParameterListener);

        ArgumentCaptor<OpenSearchStatusException> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(getInputParameterListener).onFailure(argumentCaptor.capture());
        OpenSearchStatusException exception = argumentCaptor.getValue();
        assertEquals(
            "Failed to find prompt with provided prompt reference: prompt_id for following reason: Failed to get data object from index .plugins-ml-prompt",
            exception.getMessage()
        );
        assertEquals(RestStatus.NOT_FOUND, exception.status());
    }

    @Test
    public void testBuildInputParametersFailWithIncorrectPullPromptSyntax1() {
        String exampleInput = "[{\"role\": \"user\", \"content\": \"pull_prompt(prompt_id)\"}]";

        Map<String, String> inputParameters = Map.of("messages", exampleInput, "prompt_parameters", expectedInputStrWithPromptParameters);
        mlPromptManager.buildInputParameters("messages", inputParameters, "tenant_id", getInputParameterListener);

        ArgumentCaptor<IllegalArgumentException> argumentCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(getInputParameterListener).onFailure(argumentCaptor.capture());
        IllegalArgumentException exception = argumentCaptor.getValue();
        assertEquals(
            "Invalid pull_prompt syntax is provided: pull_prompt(prompt_id). Expected: pull_prompt(prompt_id).key",
            exception.getMessage()
        );
    }

    @Test
    public void testBuildInputParametersFailWithIncorrectPullPromptSyntax2() {
        String exampleInput = "[{\"role\": \"user\", \"content\": \"ull_prompt(prompt_id).user\"}]";

        Map<String, String> inputParameters = Map.of("messages", exampleInput, "prompt_parameters", expectedInputStrWithPromptParameters);
        mlPromptManager.buildInputParameters("messages", inputParameters, "tenant_id", getInputParameterListener);

        ArgumentCaptor<IllegalArgumentException> argumentCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(getInputParameterListener).onFailure(argumentCaptor.capture());
        IllegalArgumentException exception = argumentCaptor.getValue();
        assertEquals("You typed ull_prompt. Provide Correct pull_prompt syntax: pull_prompt(prompt_id).<key>", exception.getMessage());
    }

    private GetResponse createGetResponse(String user, String system) {
        Map<String, String> prompt = new HashMap<>();
        prompt.put("user", user);
        prompt.put("system", system);
        MLPrompt mlPrompt = MLPrompt.builder().name("test prompt").prompt(prompt).build();
        XContentBuilder content;
        try {
            content = mlPrompt.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult(ML_PROMPT_INDEX, "111", 111l, 111l, 111l, true, bytesReference, null, null);
        return new GetResponse(getResult);
    }

    private SearchResponse createSearchResponse(long totalHits) throws IOException {
        SearchResponse searchResponse = mock(SearchResponse.class);
        String promptContent = "{\n"
            + "                    \"_id\": \"prompt_id\",\n"
            + "                    \"name\": \"Test Prompt\",\n"
            + "                    \"description\": \"This is an example description\",\n"
            + "                    \"version\": 1,\n"
            + "                    \"created_time\": 1684981986069,\n"
            + "                    \"last_updated_time\": 1684981986069\n"
            + "                }";
        SearchHit prompt = SearchHit.fromXContent(TestHelper.parser(promptContent));
        SearchHits hits = new SearchHits(new SearchHit[] { prompt }, new TotalHits(totalHits, TotalHits.Relation.EQUAL_TO), Float.NaN);
        when(searchResponse.getHits()).thenReturn(hits);
        return searchResponse;
    }
}
