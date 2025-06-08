/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.prompt;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.prompt.MLPrompt;
import org.opensearch.ml.common.prompt.PromptExtraConfig;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptInput;

import com.langfuse.client.LangfuseClient;
import com.langfuse.client.core.LangfuseClientApiException;
import com.langfuse.client.resources.prompts.types.ChatMessage;
import com.langfuse.client.resources.prompts.types.ChatPrompt;
import com.langfuse.client.resources.prompts.types.CreateChatPromptRequest;
import com.langfuse.client.resources.prompts.types.CreatePromptRequest;
import com.langfuse.client.resources.prompts.types.CreateTextPromptRequest;
import com.langfuse.client.resources.prompts.types.Prompt;
import com.langfuse.client.resources.prompts.types.TextPrompt;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class LangfusePromptManagement extends AbstractPromptManagement {
    public static final String PUBLIC_KEY_FIELD = "public_key";
    public static final String ACCESS_KEY_FIELD = "access_key";
    public static final String LANGFUSE_URL = "https://us.cloud.langfuse.com";
    public static final String USER_ROLE = "user";

    public final String TEXT_PROMPT = "text";
    public final String CHAT_PROMPT = "chat";
    public final String PRODUCTION_LABEL = "production";

    private LangfuseClient langfuseClient;
    private String publicKey;
    private String accessKey;

    public LangfusePromptManagement(String publicKey, String accessKey) {
        this.publicKey = publicKey;
        this.accessKey = accessKey;
        this.langfuseClient = initLangfuseClient(this.publicKey, this.accessKey);
    }

    public LangfuseClient initLangfuseClient(String username, String password) {
        return LangfuseClient.builder().url(LANGFUSE_URL).credentials(username, password).build();
    }

    @Override
    public MLPrompt createPrompt(MLCreatePromptInput mlCreatePromptInput) {
        PromptExtraConfig promptExtraConfig = mlCreatePromptInput.getPromptExtraConfig();
        String type = promptExtraConfig.getType();

        // assigns production label by default
        List<String> labels = promptExtraConfig.getLabels();
        if (labels == null) {
            labels = List.of(PRODUCTION_LABEL);
        } else {
            labels.add(PRODUCTION_LABEL);
        }
        promptExtraConfig.setLabels(labels);

        CreatePromptRequest langfuseRequest;
        switch (type) {
            case TEXT_PROMPT:
                langfuseRequest = buildTextPromptRequest(mlCreatePromptInput, promptExtraConfig);
                break;
            case CHAT_PROMPT:
                langfuseRequest = buildChatPromptRequest(mlCreatePromptInput, promptExtraConfig);
                break;
            default:
                log.error("Unable to find prompt template type");
                throw new IllegalArgumentException("Unable to find prompt template type for Langfuse");
        }
        try {
            langfuseClient.prompts().create(langfuseRequest);
            // we only need to store the fields that are necessary to retrieve this prompt later -> name, prompt management type, encrypted
            // credentials
            return MLPrompt
                    .builder()
                    .name(mlCreatePromptInput.getName())
                    .promptManagementType(mlCreatePromptInput.getPromptManagementType())
                    .promptExtraConfig(PromptExtraConfig.builder().publicKey(publicKey).accessKey(accessKey).build())
                    .build();
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            if (e instanceof LangfuseClientApiException) {
                errorMessage = getLangfuseClientExceptionMessage((LangfuseClientApiException) e);
            }
            log.error("Failed to create a prompt in Langfuse Server", e);
            throw new OpenSearchStatusException(errorMessage, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private CreatePromptRequest buildTextPromptRequest(MLCreatePromptInput mlCreatePromptInput, PromptExtraConfig promptExtraConfig) {
        CreateTextPromptRequest textRequest = CreateTextPromptRequest
                .builder()
                .name(mlCreatePromptInput.getName())
                .prompt(mlCreatePromptInput.getPrompt().get(USER_ROLE))
                .labels(promptExtraConfig.getLabels())
                .tags(mlCreatePromptInput.getTags())
                .build();

        return CreatePromptRequest.text(textRequest);
    }

    private CreatePromptRequest buildChatPromptRequest(MLCreatePromptInput mlCreatePromptInput, PromptExtraConfig promptExtraConfig) {
        List<ChatMessage> langfusePromptTemplate = new ArrayList<>();
        Map<String, String> mlPromptTemplate = mlCreatePromptInput.getPrompt();
        for (String role : mlPromptTemplate.keySet()) {
            String content = mlPromptTemplate.get(role);
            ChatMessage message = ChatMessage.builder().role(role).content(content).build();
            langfusePromptTemplate.add(message);
        }

        CreateChatPromptRequest chatRequest = CreateChatPromptRequest
                .builder()
                .name(mlCreatePromptInput.getName())
                .prompt(langfusePromptTemplate)
                .labels(promptExtraConfig.getLabels())
                .tags(mlCreatePromptInput.getTags())
                .build();

        return CreatePromptRequest.chat(chatRequest);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (publicKey != null) {
            builder.field(PUBLIC_KEY_FIELD, publicKey);
        }
        if (accessKey != null) {
            builder.field(ACCESS_KEY_FIELD, accessKey);
        }
        builder.endObject();
        return builder;
    }

    @Override
    public void getPrompt(MLPrompt mlPrompt) {
        mlPrompt.setPromptExtraConfig(null); // won't include credentials in response body
        try {
            Prompt langfusePrompt = langfuseClient.prompts().get(mlPrompt.getName());
            // check if the fetched langfuse prompt is text or chat prompt
            if (langfusePrompt.isText() && langfusePrompt.getText().isPresent()) {
                buildMLPromptFromTextPrompt(langfusePrompt.getText().get(), mlPrompt);
            } else if (langfusePrompt.isChat() && langfusePrompt.getChat().isPresent()) {
                buildMLPromptFromChatPrompt(langfusePrompt.getChat().get(), mlPrompt);
            } else {
                log.error("Error when fetching the Langfuse Prompt");
                throw new OpenSearchStatusException("Failed to get a Langfuse Prompt", RestStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            if (e instanceof LangfuseClientApiException) {
                errorMessage = getLangfuseClientExceptionMessage((LangfuseClientApiException) e);
            }
            log.error("Failed to fetch a Langfuse prompt", e);
            throw new OpenSearchStatusException("Failed to get Langfuse Prompt: " + errorMessage, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void buildMLPromptFromTextPrompt(TextPrompt textPrompt, MLPrompt mlPrompt) {
        mlPrompt.setVersion(String.valueOf(textPrompt.getVersion()));
        mlPrompt.setPrompt(Map.of(USER_ROLE, textPrompt.getPrompt()));
        mlPrompt.setTags(!textPrompt.getTags().isEmpty() ? textPrompt.getTags() : null);

        PromptExtraConfig promptExtraConfig = PromptExtraConfig.builder().type(TEXT_PROMPT).labels(textPrompt.getLabels()).build();
        mlPrompt.setPromptExtraConfig(promptExtraConfig);

        setTimeInstants(textPrompt.toString(), mlPrompt);
    }

    private void buildMLPromptFromChatPrompt(ChatPrompt chatPrompt, MLPrompt mlPrompt) {
        mlPrompt.setVersion(String.valueOf(chatPrompt.getVersion()));
        mlPrompt.setTags(chatPrompt.getTags());

        PromptExtraConfig promptExtraConfig = PromptExtraConfig.builder().type(CHAT_PROMPT).labels(chatPrompt.getLabels()).build();
        mlPrompt.setPromptExtraConfig(promptExtraConfig);

        List<ChatMessage> langfusePromptTemplate = chatPrompt.getPrompt();
        Map<String, String> mlPromptTemplate = new HashMap<>();

        for (ChatMessage message : langfusePromptTemplate) {
            if (mlPromptTemplate.containsKey(message.getRole())) {
                continue;
            }
            mlPromptTemplate.put(message.getRole(), message.getContent());
        }
        mlPrompt.setPrompt(mlPromptTemplate);

        setTimeInstants(chatPrompt.toString(), mlPrompt);
    }

    private void setTimeInstants(String fetchSurce, MLPrompt mlPrompt) {
        String createdTime = null;
        String lastUpdatedTime = null;

        try (
                XContentParser parser = jsonXContent.createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, fetchSurce)
        ) {
            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
            while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                String fieldName = parser.currentName();
                parser.nextToken();
                switch (fieldName) {
                    case "createdAt":
                        createdTime = parser.text();
                        break;
                    case "updatedAt":
                        lastUpdatedTime = parser.text();
                        break;
                    default:
                        parser.skipChildren();
                        break;
                }
            }
        } catch (Exception e) {
            throw new OpenSearchStatusException("Failed to parse Langfuse Prompt into MLPrompt", RestStatus.INTERNAL_SERVER_ERROR);
        }
        Objects.requireNonNull(createdTime, "Failed to parse Create Time");
        Objects.requireNonNull(lastUpdatedTime, "Failed to parse Last Updated Time");
        mlPrompt.setCreateTime(Instant.parse(createdTime));
        mlPrompt.setLastUpdateTime(Instant.parse(lastUpdatedTime));
    }

    private String getLangfuseClientExceptionMessage(LangfuseClientApiException langfuseClientApiException) {
        String message = langfuseClientApiException.getMessage();
        Object errorBody = langfuseClientApiException.body();
        if (errorBody instanceof LinkedHashMap<?, ?> && ((LinkedHashMap<?, ?>) errorBody).containsKey("message")) {
            message = ((LinkedHashMap<?, ?>) errorBody).get("message").toString();
        }
        return message;
    }
}