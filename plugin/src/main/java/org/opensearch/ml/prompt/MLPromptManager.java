/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.prompt;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_PROMPT_INDEX;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.text.StringSubstitutor;
import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContent;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.common.prompt.MLPrompt;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptInput;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.SearchDataObjectRequest;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.transport.client.Client;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

/**
 * MLPromptManager is responsible for managing MLPrompt.
 */
@Log4j2
public class MLPromptManager {
    public static final int MAX_NUMBER_OF_TAGS = 20;
    public static final int MAX_LENGTH_OF_TAG = 35;
    public static final String TAG_RESTRICTION_ERR_MESSAGE = ("Number of tags must not exceed "
        + MAX_NUMBER_OF_TAGS
        + " and length of each tag must not exceed "
        + MAX_LENGTH_OF_TAG);

    public static final String ROLE_PARAMETER = "role";
    public static final String CONTENT_PARAMETER = "content";

    public static final String PARAMETERS_PROMPT_FIELD = "prompt";
    public static final String PARAMETERS_MESSAGES_FIELD = "messages";
    public static final String PARAMETERS_PROMPT_PARAMETERS_FIELD = "prompt_parameters";

    private final Client client;
    private final SdkClient sdkClient;

    public MLPromptManager(@NonNull Client client, @NonNull SdkClient sdkClient) {
        this.client = Objects.requireNonNull(client, "Client cannot be null");
        this.sdkClient = Objects.requireNonNull(sdkClient, "SdkClient cannot be null");
    }

    /**
     * Gets a prompt with the provided clients asynchronously.
     *
     * @param getDataObjectRequest The get request
     * @param promptId The prompt ID
     * @param listener the action listener to complete with the GetResponse or Exception
     */
    public void getPromptAsync(GetDataObjectRequest getDataObjectRequest, String promptId, ActionListener<MLPrompt> listener) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            sdkClient.getDataObjectAsync(getDataObjectRequest).whenComplete((getAsyncResponse, throwable) -> {
                context.restore();
                handleAsyncResponse(getAsyncResponse, throwable, listener);
            });
        }
    }

    /**
     * Handles the get prompt async response
     *
     * @param getAsyncResponse the get prompt async response
     * @param throwable the throwable from the get prompt async response
     * @param listener the listener to be notified when the get prompt async response is handled
     */
    private void handleAsyncResponse(GetDataObjectResponse getAsyncResponse, Throwable throwable, ActionListener<MLPrompt> listener) {
        if (throwable != null) {
            Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
            listener.onFailure(cause);
            return;
        }
        processResponse(getAsyncResponse, listener);
    }

    /**
     * Handles the Exception thrown and notify the listener with the failure.
     *
     * @param exception the exception from the get prompt async response
     * @param promptId the prompt id of prompts that needed to be retrieved
     * @param listener the listener to be notified when the throwable is handled
     */
    public static <T extends ActionResponse> void handleFailure(
        Exception exception,
        String promptId,
        ActionListener<T> listener,
        String likelyCause
    ) {
        if (ExceptionsHelper.unwrap(exception, IndexNotFoundException.class) != null) {
            log.error("Failed to get prompt index", exception);
            listener
                .onFailure(
                    new OpenSearchStatusException("Failed to find prompt with the provided prompt id: " + promptId, RestStatus.NOT_FOUND)
                );
        } else {
            log.error(likelyCause, promptId, exception);
            listener.onFailure(exception);
        }
    }

    /**
     * Parse the successfully retrieved GetDataObjectResponse to get the MLPrompt object, and notify the listener with it.
     *
     * @param getAsyncResponse the GetDataObjectResponse that needs to be parsed
     * @param listener the listener to be notified with the MLPrompt object
     */
    private void processResponse(GetDataObjectResponse getAsyncResponse, ActionListener<MLPrompt> listener) {
        GetResponse getResponse = getAsyncResponse.getResponse();
        try (
            XContentParser parser = jsonXContent
                .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, getResponse.getSourceAsString())
        ) {
            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
            MLPrompt mlPrompt = MLPrompt.parse(parser);
            listener.onResponse(mlPrompt);
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    /**
     * Checks if the tags exceed max number of tags or max length of tag.
     *
     * @param tags tags passed in via MLCreatePromptInput or MLUpdatePromptInput
     * @return true if the tags are valid, false otherwise
     */
    public static boolean validateTags(List<String> tags) {
        return tags.size() <= MAX_NUMBER_OF_TAGS && tags.stream().allMatch(tag -> tag.length() <= MAX_LENGTH_OF_TAG);
    }

    public void validateUniquePromptName(String name, String tenantId, ActionListener<SearchResponse> listener) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            BoolQueryBuilder query = new BoolQueryBuilder();
            query.filter(new TermQueryBuilder(MLCreatePromptInput.PROMPT_NAME_FIELD + ".keyword", name));

            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(query);
            SearchRequest searchRequest = new SearchRequest(ML_PROMPT_INDEX).source(searchSourceBuilder);

            SearchDataObjectRequest searchDataObjectRequest = SearchDataObjectRequest
                .builder()
                .indices(searchRequest.indices())
                .searchSourceBuilder(searchRequest.source())
                .tenantId(tenantId)
                .build();

            sdkClient.searchDataObjectAsync(searchDataObjectRequest).whenComplete((r, throwable) -> {
                if (throwable != null) {
                    Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                    if (ExceptionsHelper.unwrap(throwable, IndexNotFoundException.class) != null) {
                        log.debug("ML Prompt index does not exist");
                        listener.onResponse(null);
                    } else {
                        log.error("Failed to search ML Prompt index", cause);
                        listener.onFailure(cause);
                    }
                } else {
                    try {
                        SearchResponse searchResponse = r.searchResponse();
                        // Parsing failure would cause NPE on next line
                        log.info("ML Prompt search complete: {}", searchResponse.getHits().getTotalHits());
                        listener.onResponse(searchResponse);
                    } catch (Exception e) {
                        log.error("Failed to parse search response", e);
                        listener
                            .onFailure(new OpenSearchStatusException("Failed to parse search response", RestStatus.INTERNAL_SERVER_ERROR));
                    }
                }
            });
        } catch (Exception e) {
            log.error("Failed to search ML Prompt index", e);
            listener.onFailure(e);
        }
    }

    /**
     *  Builds a new map containing modified request body after pull_prompt is invoked
     *
     * @param promptType type of prompt, either prompt or messages
     * @param inputParameters a map containing full request body received during predict
     * @param tenantId tenant id
     * @param listener the listener to notified with new map containing modified request body
     */
    public void buildInputParameters(
        String promptType,
        Map<String, String> inputParameters,
        String tenantId,
        ActionListener<Map<String, String>> listener
    ) {
        try {
            Map<String, String> parameters = new HashMap<>();
            parameters.putAll(inputParameters);
            parameters.remove(PARAMETERS_PROMPT_PARAMETERS_FIELD);
            String prompt = inputParameters.get(promptType);
            String JsonStrPromptParameters = inputParameters.get(PARAMETERS_PROMPT_PARAMETERS_FIELD);
            PromptParameters promptParam = PromptParameters.buildPromptParameters(JsonStrPromptParameters);
            switch (promptType) {
                case PARAMETERS_PROMPT_FIELD:
                    String promptId = prompt.split("\\(")[1].split("\\)")[0];
                    String key = prompt.split("\\.")[1];
                    parameters.put(PARAMETERS_PROMPT_FIELD, pullPrompt(promptId, key, promptParam, tenantId));
                    break;
                case PARAMETERS_MESSAGES_FIELD:
                    Messages messages = Messages.buildMessages(prompt);
                    for (Message message : messages.messages) {
                        if (!message.getKey().equals(message.getRole())) {
                            throw new IllegalArgumentException("Specified key does not match the provided role");
                        }
                        String content = pullPrompt(message.getPromptId(), message.getKey(), promptParam, tenantId);
                        message.setContent(content);
                    }
                    parameters.put(PARAMETERS_MESSAGES_FIELD, Messages.toJsonString(messages));
                    break;
                default:
                    log.error("Wrong prompt type is provided: {}, should provide either prompt or messages", promptType);
                    throw new IllegalArgumentException(
                        "Wrong prompt type is provided: " + promptType + ", should provide either prompt or messages"
                    );
            }
            listener.onResponse(parameters);
        } catch (Exception exception) {
            if (exception instanceof ArrayIndexOutOfBoundsException) {
                exception = new IllegalArgumentException(
                    "Forgot to provide a key. Provide a correct pull_prompt syntax: pull_prompt(prompt_id).<key>"
                );
            }
            log.error("Failed to build a new Input Parameters: ", exception);
            listener.onFailure(exception);
        }
    }

    /**
     * Fetches the ML Prompt based on prompt id, then replace the content with the retrieved content from prompt
     * template based on the specified key.
     *
     * @param promptId prompt id
     * @param key key for the user-defined content
     * @param promptParameters prompt parameters
     * @param tenantId tenant id
     * @return the user-defined content
     * @throws IllegalArgumentException if
     *      1. fails to replace all the placeholder variables.
     *      2. the specified key is not defined in the prompt template.
     *      3. Failed to build a prompt or messages instance
     *      3. Incorrect pull_prompt(prompt_id).<key> syntax is provided
     *      4. Provided role does not match specified key during predict
     * @throws OpenSearchStatusException if the ML Prompt is not found
     */
    public String pullPrompt(String promptId, String key, PromptParameters promptParameters, String tenantId) throws IOException {
        GetDataObjectRequest getDataObjectRequest = GetDataObjectRequest
            .builder()
            .index(ML_PROMPT_INDEX)
            .id(promptId)
            .tenantId(tenantId)
            .build();
        try {
            // fetch prompt first based on prompt id
            MLPrompt mlPrompt = getPrompt(getDataObjectRequest);
            Map<String, String> promptField = mlPrompt.getPrompt();
            // check if the specified key is defined in the prompt
            if (!promptField.containsKey(key)) {
                throw new IllegalArgumentException("Content for specified key is not defined in ML Prompt: " + promptId);
            }
            // retrieve the user-defined content during prompt creation
            String content = promptField.get(key);
            // populate the placeholder variable with user input, if needed
            if (!promptParameters.isEmpty() && content.contains("${prompt_parameters.")) {
                StringSubstitutor substitutor = new StringSubstitutor(
                    promptParameters.getParameters(promptId),
                    "${prompt_parameters.",
                    "}"
                );
                content = substitutor.replace(content);
            }
            // this checks if all the required input values are provided by users.
            if (content.contains("${prompt_parameters.")) {
                throw new IllegalArgumentException("Failed to replace all the placeholders for prompt: " + promptId);
            }
            return content;
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw e;
            }
            throw new OpenSearchStatusException("Failed to find a ML Prompt with prompt id: " + promptId, RestStatus.NOT_FOUND);
        }
    }

    /**
     * Retrieve ML Prompt synchronously
     *
     * @param getDataObjectRequest GetDataObjectRequest that is being sent to retrieve a prompt
     */
    public MLPrompt getPrompt(GetDataObjectRequest getDataObjectRequest) throws IOException {
        GetDataObjectResponse getPromptResponse = sdkClient.getDataObject(getDataObjectRequest);
        try (
            XContentParser parser = XContentType.JSON
                .xContent()
                .createParser(
                    NamedXContentRegistry.EMPTY,
                    LoggingDeprecationHandler.INSTANCE,
                    getPromptResponse.getResponse().getSourceAsString()
                )
        ) {
            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
            return MLPrompt.parse(parser);
        } catch (Exception ex) {
            log.error("Failed to parse ML Prompt");
            throw ex;
        }
    }

    /**
     * A class that represents a list of messages.
     */
    static class Messages {
        private List<Message> messages;

        @Builder(toBuilder = true)
        public Messages(List<Message> messages) {
            this.messages = messages;
        }

        // convert into json format string
        public static String toJsonString(Messages messages) {
            List<Map<String, String>> jsonString = new ArrayList<>();
            for (Message message : messages.messages) {
                Map<String, String> messageMap = new HashMap<>();
                messageMap.put(ROLE_PARAMETER, message.getRole());
                messageMap.put(CONTENT_PARAMETER, message.getContent());
                jsonString.add(messageMap);
            }
            return StringUtils.toJson(jsonString);
        }

        public int size() {
            return this.messages.size();
        }

        // initialize a Messages instance from json format input string
        public static Messages buildMessages(String input) throws IOException {
            List<Message> messages = new ArrayList<>();
            XContent xContent = XContentType.JSON.xContent();
            try (
                XContentParser parser = xContent
                    .createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.THROW_UNSUPPORTED_OPERATION, input)
            ) {
                ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.nextToken(), parser);
                while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                    messages.add(Message.buildMessage(parser));
                }
            } catch (Exception ex) {
                log.error("Failed to build Messages");
                throw ex;
            }
            return Messages.builder().messages(messages).build();
        }
    }

    /**
     * A class that represents a message.
     *
     */
    @Data
    static class Message {
        /*
            e.g. input before parsing:
            {
                "role": "user",
                "content": "pull_prompt(prompt_id).<key>"
            }
        
            After parsing:
        
            this.role = user
            this.content = pull_prompt(prompt_id).<key>
            this.promptId = prompt_id
            this.key = <key>
         */
        private String role;
        private String content;
        private String promptId;
        private String key;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
            this.promptId = this.content.split("\\(")[1].split("\\)")[0];
            this.key = this.content.split("\\.")[1];
        }

        public static Message buildMessage(XContentParser parser) throws IOException {
            String role = null;
            String content = null;

            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
            while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                String fieldName = parser.currentName();
                parser.nextToken();
                switch (fieldName) {
                    case ROLE_PARAMETER:
                        role = parser.text();
                        break;
                    case CONTENT_PARAMETER:
                        content = parser.text();
                        // check if the correct pull_prompt syntax is provided
                        if (!content.contains("pull_prompt")) {
                            throw new IllegalArgumentException(
                                "You typed "
                                    + content.split("\\(")[0]
                                    + ". Provide Correct pull_prompt syntax: pull_prompt(prompt_id).<key>"
                            );
                        }
                        break;
                    default:
                        parser.skipChildren();
                        break;
                }
            }
            return new Message(role, content);
        }
    }

    /**
     * A class that represents prompt parameters.
     */
    static class PromptParameters {
        /*
            e.g. input before parsing:
            {
                "prompt_id":
                {
                    "name": "jeff"
                }
            }
        
            After parsing:
        
            this.parameters = Map.of("name", "jeff")
         */
        private final Map<String, Map<String, String>> parameters;

        @Builder(toBuilder = true)
        public PromptParameters(Map<String, Map<String, String>> parameters) {
            this.parameters = parameters;
        }

        public Map<String, String> getParameters(String promptId) {
            return this.parameters.get(promptId);
        }

        public boolean isEmpty() {
            return this.parameters.isEmpty();
        }

        public static PromptParameters buildPromptParameters(String input) throws IOException {
            Map<String, Map<String, String>> promptParam = new HashMap<>();
            XContent xContent = XContentType.JSON.xContent();
            try (
                XContentParser parser = xContent
                    .createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.THROW_UNSUPPORTED_OPERATION, input)
            ) {
                ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                    String promptId = parser.currentName();
                    parser.nextToken();
                    Map<String, String> parameter = StringUtils.getParameterMap(parser.map());
                    promptParam.put(promptId, parameter);
                }
            }
            return PromptParameters.builder().parameters(promptParam).build();
        }
    }
}
