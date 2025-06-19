/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.prompt;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_PROMPT_INDEX;
import static org.opensearch.ml.common.prompt.MLPrompt.LANGFUSE;
import static org.opensearch.ml.prompt.AbstractPromptManagement.init;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.text.StringSubstitutor;
import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.collect.Tuple;
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
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.prompt.MLPrompt;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptInput;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.utils.MLExceptionUtils;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.SearchDataObjectRequest;
import org.opensearch.remote.metadata.client.SearchDataObjectResponse;
import org.opensearch.remote.metadata.client.UpdateDataObjectRequest;
import org.opensearch.remote.metadata.client.UpdateDataObjectResponse;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.transport.client.Client;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
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
    public static final String UNIQUE_NAME_ERR_MESSAGE = ("The name you provided is already being used by another Prompt with ID: ");

    public static final String ROLE_PARAMETER = "role";
    public static final String CONTENT_PARAMETER = "content";

    public static final String PARAMETERS_PROMPT_FIELD = "prompt";
    public static final String PARAMETERS_MESSAGES_FIELD = "messages";
    public static final String PARAMETERS_PROMPT_PARAMETERS_FIELD = "prompt_parameters";
    public static final String PROMPT_PARAMETER_PLACEHOLDER = "${prompt_parameters.";

    private final Client client;
    private final SdkClient sdkClient;
    private final EncryptorImpl encryptor;

    public MLPromptManager(@NonNull Client client, @NonNull SdkClient sdkClient, EncryptorImpl encryptor) {
        this.client = Objects.requireNonNull(client, "Client cannot be null");
        this.sdkClient = Objects.requireNonNull(sdkClient, "SdkClient cannot be null");
        this.encryptor = encryptor;
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
     *
     *
     * @param tags tags passed in via MLCreatePromptInput or MLUpdatePromptInput
     * @return true if the tags are valid, false otherwise
     */
    public static boolean validateTags(List<String> tags) {
        return tags.size() <= MAX_NUMBER_OF_TAGS && tags.stream().allMatch(tag -> tag.length() <= MAX_LENGTH_OF_TAG);
    }

    /**
     * Searches the prompt index for prompts that exactly match the given name, using the name field.
     *
     * @param name The prompt name to search for
     * @param tenantId tenant id
     * @return SearchResponse containing any matching prompts, or an exception if the search fails.
     */
    public SearchResponse searchPromptByName(String name, String tenantId) {
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

            SearchDataObjectResponse searchDataObjectResponse = sdkClient.searchDataObject(searchDataObjectRequest);
            SearchResponse searchResponse = searchDataObjectResponse.searchResponse();
            return searchResponse;

        } catch (Exception e) {
            if (e instanceof IndexNotFoundException || MLExceptionUtils.getRootCause(e) instanceof IndexNotFoundException) {
                log.debug("ML Prompt index does not exist. ML Prompt name: {} will be used to init ML Prompt index", name);
                return null;
            }
            throw new IllegalArgumentException("Failed to search ML Prompt index due to following cause: " + e);
        }
    }

    /**
     * Returns the boolean statement whether the name already exists or not based on retrieved Search Response
     *
     * @param searchResponse SearchResponse
     * @return True if the name already exists in the prompt system index, False Otherwise.
     */
    public static boolean MLPromptNameAlreadyExists(SearchResponse searchResponse) {
        return searchResponse != null
            && searchResponse.getHits().getTotalHits() != null
            && searchResponse.getHits().getTotalHits().value() != 0;
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
            Map<String, String> parameters = new HashMap<>(inputParameters);
            parameters.remove(PARAMETERS_PROMPT_PARAMETERS_FIELD);
            String inputContent = inputParameters.get(promptType);
            if (inputContent == null || inputContent.trim().isEmpty()) {
                throw new IllegalArgumentException("Missing required input: Either prompt or messages must be provided");
            }
            String JsonStrPromptParameters = inputParameters.get(PARAMETERS_PROMPT_PARAMETERS_FIELD);
            PromptParameters promptParam = PromptParameters.buildPromptParameters(JsonStrPromptParameters);
            switch (promptType) {
                case PARAMETERS_PROMPT_FIELD:
                    handlePromptField(parameters, inputContent, promptParam, tenantId);
                    break;
                case PARAMETERS_MESSAGES_FIELD:
                    handleMessagesField(parameters, inputContent, promptParam, tenantId);
                    break;
                default:
                    log.error("Wrong prompt type is provided: {}, should provide either prompt or messages", promptType);
                    throw new IllegalArgumentException(
                        "Wrong prompt type is provided: " + promptType + ", should provide either prompt or messages"
                    );
            }
            listener.onResponse(parameters);
        } catch (Exception exception) {
            log.error("Failed to build a new Input Parameters: ", exception);
            listener.onFailure(exception);
        }
    }

    /**
     * Convert prompt field content into an actual data fetched from ML Prompt based on provided prompt reference
     *
     * @param parameters Parameters field passed in by user in its request body during model execution
     * @param promptContent content of prompt field
     * @param promptParam Prompt Parameters field that holds user-defined values to placeholder variables
     * @param tenantId tenant id
     */
    private void handlePromptField(Map<String, String> parameters, String promptContent, PromptParameters promptParam, String tenantId)
        throws IOException {
        Tuple<String, String> IDAndKey = validatePullPromptSyntax(promptContent);
        String promptId = IDAndKey.v1();
        String key = IDAndKey.v2();
        PromptResult promptResult = pullPrompt(promptId, key, promptParam, tenantId);
        parameters.put(PARAMETERS_PROMPT_FIELD, promptResult.getContent());
    }

    /**
     * Convert messages field content into an actual data fetched from ML Prompt based on provided prompt reference
     *
     * @param parameters Parameters field passed in by user in its request body during model execution
     * @param messagesContent content of prompt field
     * @param promptParam Prompt Parameters field that holds user-defined values to placeholder variables
     * @param tenantId tenant id
     */
    private void handleMessagesField(Map<String, String> parameters, String messagesContent, PromptParameters promptParam, String tenantId)
        throws IOException {
        Messages messages = Messages.buildMessages(messagesContent);
        for (Message message : messages.messages) {
            if (!message.getKey().equals(message.getRole())) {
                throw new InvalidPullPromptSyntaxException("Specified key does not match the provided role");
            }
            PromptResult promptResult = pullPrompt(message.getPromptId(), message.getKey(), promptParam, tenantId);
            message.setContent(promptResult.getContent());
        }
        parameters.put(PARAMETERS_MESSAGES_FIELD, Messages.toJsonString(messages));
    }

    /**
     * Validate pull_prompt syntax and Retrieves prompt reference and key that are needed to retrieve a specific prompt
     *
     * @param input input that contains pull_prompt syntax alongside prompt reference and key
     * @return List that contains prompt reference and key
     * @throws InvalidPullPromptSyntaxException if invalid syntax is provided
     */
    private static Tuple<String, String> validatePullPromptSyntax(String input) {
        if (input != null && input.contains("pull_prompt(")) {
            String pullPromptRegex = "pull_prompt\\(([^)]+)\\)\\.(.+)";

            Pattern pattern = Pattern.compile(pullPromptRegex);
            Matcher matcher = pattern.matcher(input);

            if (!matcher.matches()) {
                throw new InvalidPullPromptSyntaxException(
                    "Invalid pull_prompt syntax is provided: " + input + ". Expected: pull_prompt(prompt_id).key"
                );
            }
            String promptId = matcher.group(1);
            String key = matcher.group(2);

            return new Tuple<String, String>(promptId, key);
        }
        throw new InvalidPullPromptSyntaxException(
            "Invalid pull_prompt syntax is provided: " + input + ". Expected: pull_prompt(prompt_id).key"
        );
    }

    /**
     * Fetches the ML Prompt based on prompt id, then replace the content with the retrieved content from prompt
     * template based on the specified key.
     *
     * @param promptRef prompt reference that is used to retrieve a prompt. Either prompt ID or prompt name
     * @param key key for the user-defined content
     * @param promptParameters prompt parameters
     * @param tenantId tenant id
     * @throws IllegalArgumentException if
     * <p>
     *     1. fails to replace all the placeholder variables.
     *     2. the specified key is not defined in the prompt template.
     *     3. Failed to build a prompt or messages instance
     *     4. Incorrect pull_prompt(prompt_id).key syntax is provided
     *     5. Provided role does not match specified key during predict
     * </p>
     * @throws OpenSearchStatusException if the ML Prompt is not found
     */
    public PromptResult pullPrompt(String promptRef, String key, PromptParameters promptParameters, String tenantId) throws IOException {
        try {
            String promptId = resolvePromptID(promptRef, tenantId);
            GetDataObjectRequest getDataObjectRequest = GetDataObjectRequest
                .builder()
                .index(ML_PROMPT_INDEX)
                .id(promptId)
                .tenantId(tenantId)
                .build();
            // fetch prompt first based on prompt id
            MLPrompt mlPrompt = getPrompt(getDataObjectRequest);
            // enables user execute the prompt in external prompt management server that is created via ml commons create, without importing
            // it
            if (fetchPromptExternally(mlPrompt)) {
                mlPrompt.decrypt(mlPrompt.getPromptManagementType(), encryptor::decrypt, tenantId);
                AbstractPromptManagement promptManagement = init(mlPrompt.getPromptManagementType(), mlPrompt.getPromptExtraConfig());
                promptManagement.getPrompt(mlPrompt);
            }
            // extract a prompt object from retrieved ML Prompt
            Map<String, String> promptField = mlPrompt.getPrompt();
            // check if the specified key is defined in the prompt
            if (!promptField.containsKey(key)) {
                throw new InvalidPullPromptSyntaxException("Content for specified key is not defined in ML Prompt");
            }
            // retrieve the user-defined content based on provided key (role)
            String content = promptField.get(key);
            // populate the placeholder variable with user input, if needed
            content = populatePlaceholders(content, promptParameters, promptRef);
            return new PromptResult(content, promptId);
        } catch (InvalidPullPromptSyntaxException e) {
            throw new OpenSearchStatusException(
                "Failed to process prompt: " + promptRef + " for following reason: " + e.getMessage(),
                RestStatus.BAD_REQUEST
            );
        } catch (OpenSearchStatusException e) {
            throw new OpenSearchStatusException(
                "Failed to find prompt with provided prompt reference: " + promptRef + " for following reason: " + e.getMessage(),
                RestStatus.NOT_FOUND
            );
        } catch (PromptParsingException e) {
            throw new OpenSearchStatusException(
                "Failed to retrieve prompt: " + promptRef + " for following reason: " + e.getMessage(),
                RestStatus.INTERNAL_SERVER_ERROR
            );
        } catch (Exception e) {
            log.error("Unexpected error processing prompt: {}", promptRef, e);
            throw new OpenSearchStatusException("Unexpected error processing prompt: " + promptRef, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Resolve provided prompt reference into prompt ID, if name is provided
     *
     * @param promptRef name or prompt id
     * @param tenantId tenant id
     * @return prompt id after prompt reference is successfully resolved from name to id
     */
    private String resolvePromptID(String promptRef, String tenantId) {
        SearchResponse searchResponse = searchPromptByName(promptRef, tenantId);
        String promptId = promptRef;
        // resolves prompt reference to an prompt id from prompt name
        if (MLPromptNameAlreadyExists(searchResponse)) {
            promptId = searchResponse.getHits().getAt(0).getId();
        }
        return promptId;
    }

    /**
     * Checks if the prompt type is Langfuse
     *
     * @param mlPrompt prompt that is either MLPrompt or LangfusePrompt
     * @return true if the given prompt is Langfuse prompt, false otherwise.
     */
    boolean fetchPromptExternally(MLPrompt mlPrompt) {
        if (mlPrompt.getPromptManagementType().equalsIgnoreCase(LANGFUSE) && mlPrompt.getPrompt() == null) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Replace all the placeholder variables with user-defined values provided during execution time.
     *
     * @param content content that contains placeholder variables
     * @param promptParameters prompt parameter field that contains all the values to provided placeholder variables
     * @param promptRef prompt reference provided by user, either name or prompt id
     * @return
     */
    private String populatePlaceholders(String content, PromptParameters promptParameters, String promptRef) {
        StringSubstitutor substitutor = new StringSubstitutor();
        if (!promptParameters.isEmpty() && content.contains(PROMPT_PARAMETER_PLACEHOLDER)) {
            substitutor = new StringSubstitutor(promptParameters.getParameters(promptRef), PROMPT_PARAMETER_PLACEHOLDER, "}");
            content = substitutor.replace(content);
        } else if (!promptParameters.isEmpty() && content.contains("{{") && content.contains("}}")) {
            substitutor = new StringSubstitutor(promptParameters.getParameters(promptRef), "{{", "}}");
        }
        content = substitutor.replace(content);

        // this checks if all the required input values are provided by users and all the placeholder variables are replaced.
        if (content.contains(PROMPT_PARAMETER_PLACEHOLDER) || (content.contains("{{") && content.contains("}}"))) {
            throw new InvalidPullPromptSyntaxException("Failed to replace all the placeholders");
        }
        return content;
    }

    /**
     * Retrieve ML Prompt synchronously
     *
     * @param getDataObjectRequest GetDataObjectRequest that is being sent to retrieve a prompt
     */
    public MLPrompt getPrompt(GetDataObjectRequest getDataObjectRequest) {
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
        } catch (IOException ex) {
            log.error("Failed to parse ML Prompt");
            throw new PromptParsingException(
                "Failed to parse ML Prompt data in Json format into MLPrompt due to following cause:  + ex.getMessage()"
            );
        }
    }

    /**
     * Updates the prompt based on the update contents and replace the old prompt with updated prompt from the index
     *
     * @param updateDataObjectRequest the updateRequest that needs to be handled
     * @param promptId The prompt ID of a prompt that needs to be updated
     * @param listener a listener to be notified of the response
     */
    public void updatePromptIndex(
        UpdateDataObjectRequest updateDataObjectRequest,
        String promptId,
        ActionListener<UpdateResponse> listener
    ) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            sdkClient.updateDataObjectAsync(updateDataObjectRequest).whenComplete((updateDataObjectResponse, throwable) -> {
                context.restore();
                handleUpdateResponse(updateDataObjectResponse, throwable, promptId, listener);
            });
        }
    }

    /**
     * Handles the response from the update prompt request. If the response is successful, notify the listener
     * with the UpdateResponse. Otherwise, notify the failure exception to the listener.
     *
     * @param updateDataObjectResponse The response from the update prompt request
     * @param throwable The exception that occurred during the update prompt request
     * @param listener The listener to be notified of the response
     */
    private void handleUpdateResponse(
        UpdateDataObjectResponse updateDataObjectResponse,
        Throwable throwable,
        String promptId,
        ActionListener<UpdateResponse> listener
    ) {
        if (throwable != null) {
            Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
            handleFailure(cause, promptId, listener, "Failed to update ML prompt {}");
            return;
        }
        UpdateResponse updateResponse = updateDataObjectResponse.updateResponse();
        listener.onResponse(updateResponse);
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
            Objects.requireNonNull(messages, "Messages content cannot be null when converting to Json");
            int totalMessages = messages.messages.size();
            StringBuilder jsonString = new StringBuilder();
            jsonString.append("[");
            for (int i = 0; i < totalMessages; i++) {
                Message message = messages.messages.get(i);
                jsonString.append("{");
                jsonString.append("\"" + ROLE_PARAMETER + "\":\"").append(message.getRole()).append("\",");
                jsonString.append("\"" + CONTENT_PARAMETER + "\":\"").append(message.getContent()).append("\"");
                jsonString.append(i == totalMessages - 1 ? "}" : "},");
            }
            jsonString.append("]");
            return jsonString.toString();
        }

        public int size() {
            return this.messages.size();
        }

        // initialize a Messages instance from json format input string
        public static Messages buildMessages(String input) {
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
            } catch (IOException ex) {
                throw new PromptParsingException(
                    "Failed to parse Messages Json format into Messages instance due to following cause: " + ex.getMessage()
                );
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

        public Message(@NonNull String role, @NonNull String content) {
            this.role = Objects.requireNonNull(role, "Missing message role field. Expecting either user or system role");
            this.content = Objects
                .requireNonNull(content, "Missing message content field. Expecting message content based on provided role");
            Tuple<String, String> IDAndKey = validatePullPromptSyntax(this.content);
            this.promptId = IDAndKey.v1();
            this.key = IDAndKey.v2();
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
                            throw new InvalidPullPromptSyntaxException(
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

        public static PromptParameters buildPromptParameters(String input) {
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
                return PromptParameters.builder().parameters(promptParam).build();
            } catch (IOException e) {
                throw new PromptParsingException(
                    "Failed to parse Prompt Parameters Json format into PromptParameters due to following cause: " + e.getMessage()
                );
            }
        }
    }

    /**
     * A class that represents Prompt result that contains content after pull prompt op is complete
     */
    @Getter
    static class PromptResult {
        private final String content;
        private final String promptId;

        public PromptResult(String content, String promptId) {
            this.content = content;
            this.promptId = promptId;
        }
    }

    /**
     * An Exception to be thrown when invalid pull prompt syntax is provided
     */
    static class InvalidPullPromptSyntaxException extends IllegalArgumentException {
        public InvalidPullPromptSyntaxException(String message) {
            super(message);
        }
    }

    /**
     * An Exception to be thrown when parsing fails during ML Prompt management
     */
    static class PromptParsingException extends MLException {
        public PromptParsingException(String message) {
            super(message);
        }
    }
}
