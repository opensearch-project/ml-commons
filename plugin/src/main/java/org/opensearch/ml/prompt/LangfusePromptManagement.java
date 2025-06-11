/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.prompt;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_PROMPT_INDEX;
import static org.opensearch.ml.common.prompt.MLPrompt.LANGFUSE;
import static org.opensearch.ml.common.prompt.MLPrompt.MLPROMPT;
import static org.opensearch.ml.prompt.MLPromptManagement.INITIAL_VERSION;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.prompt.MLPrompt;
import org.opensearch.ml.common.prompt.PromptExtraConfig;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptInput;
import org.opensearch.ml.common.transport.prompt.MLImportPromptInput;

import com.langfuse.client.LangfuseClient;
import com.langfuse.client.core.LangfuseClientApiException;
import com.langfuse.client.resources.prompts.requests.GetPromptRequest;
import com.langfuse.client.resources.prompts.requests.ListPromptsMetaRequest;
import com.langfuse.client.resources.prompts.types.ChatMessage;
import com.langfuse.client.resources.prompts.types.ChatPrompt;
import com.langfuse.client.resources.prompts.types.CreateChatPromptRequest;
import com.langfuse.client.resources.prompts.types.CreatePromptRequest;
import com.langfuse.client.resources.prompts.types.CreateTextPromptRequest;
import com.langfuse.client.resources.prompts.types.Prompt;
import com.langfuse.client.resources.prompts.types.PromptMeta;
import com.langfuse.client.resources.prompts.types.PromptMetaListResponse;
import com.langfuse.client.resources.prompts.types.TextPrompt;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.opensearch.ml.common.transport.prompt.MLUpdatePromptInput;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.UpdateDataObjectRequest;

@Log4j2
@Getter
public class LangfusePromptManagement extends AbstractPromptManagement {
    public static final String PUBLIC_KEY_FIELD = "public_key";
    public static final String ACCESS_KEY_FIELD = "access_key";
    public static final String LANGFUSE_URL = "https://us.cloud.langfuse.com";
    public static final String USER_ROLE = "user";
    public static final String DEFAULT_LIMIT = "20";

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
            Prompt promptWithInitialVersion = langfuseClient
                .prompts()
                .get(mlPrompt.getName(), GetPromptRequest.builder().version(Integer.parseInt(INITIAL_VERSION)).build());

            // check if the fetched langfuse prompt is text or chat prompt
            if (langfusePrompt.isText() && langfusePrompt.getText().isPresent()) {
                buildMLPromptFromTextPrompt(langfusePrompt.getText().get(), mlPrompt, promptWithInitialVersion.getText().get());
            } else if (langfusePrompt.isChat() && langfusePrompt.getChat().isPresent()) {
                buildMLPromptFromChatPrompt(langfusePrompt.getChat().get(), mlPrompt, promptWithInitialVersion.getChat().get());
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

    private void buildMLPromptFromTextPrompt(TextPrompt textPrompt, MLPrompt mlPrompt, TextPrompt promptWithInitialVersion) {
        mlPrompt.setVersion(String.valueOf(textPrompt.getVersion()));
        mlPrompt.setPrompt(Map.of(USER_ROLE, textPrompt.getPrompt()));
        mlPrompt.setTags(!textPrompt.getTags().isEmpty() ? textPrompt.getTags() : null);

        PromptExtraConfig promptExtraConfig = PromptExtraConfig.builder().type(TEXT_PROMPT).labels(textPrompt.getLabels()).build();
        mlPrompt.setPromptExtraConfig(promptExtraConfig);

        // get initial created Time set when initial version prompt is created
        setTimeInstants(promptWithInitialVersion.toString(), mlPrompt);
        setTimeInstants(textPrompt.toString(), mlPrompt);
    }

    private void buildMLPromptFromChatPrompt(ChatPrompt chatPrompt, MLPrompt mlPrompt, ChatPrompt promptWithInitialVersion) {
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

        // get initial created Time set when initial version prompt is created
        setTimeInstants(promptWithInitialVersion.toString(), mlPrompt);
        setTimeInstants(chatPrompt.toString(), mlPrompt);
    }

    private void setTimeInstants(String fetchSource, MLPrompt mlPrompt) {
        int version = 0;
        String createdTime = null;
        String lastUpdatedTime = null;

        try (
            XContentParser parser = jsonXContent.createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, fetchSource)
        ) {
            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
            while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                String fieldName = parser.currentName();
                parser.nextToken();
                switch (fieldName) {
                    case "version":
                        version = parser.intValue();
                    case "createdAt":
                        if (version == Integer.parseInt(INITIAL_VERSION)) {
                            createdTime = parser.text();
                        }
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
        if (createdTime != null) {
            mlPrompt.setCreateTime(Instant.parse(createdTime));
        }
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

    @Override
    public List<MLPrompt> importPrompts(MLImportPromptInput mlImportPromptInput) {
        String name = mlImportPromptInput.getName();
        String tag = mlImportPromptInput.getTag();
        String limit = mlImportPromptInput.getLimit() == null ? DEFAULT_LIMIT : mlImportPromptInput.getLimit();

        try {
            if (name != null) {
                MLPrompt mlPrompt = MLPrompt.builder().name(name).promptManagementType(LANGFUSE).build();
                getPrompt(mlPrompt);
                mlPrompt.setPromptExtraConfig(PromptExtraConfig.builder().accessKey(this.accessKey).publicKey(this.publicKey).build());
                return List.of(mlPrompt);
            }

            PromptMetaListResponse promptMetaListResponse = langfuseClient
                    .prompts()
                    .list(ListPromptsMetaRequest.builder().tag(tag).limit(Integer.parseInt(limit)).build());

            List<PromptMeta> promptMetas = promptMetaListResponse.getData();
            List<MLPrompt> mlPromptList = new ArrayList<>();

            // There is no langfuse prompts created in the provided environment
            if (promptMetas.isEmpty()) {
                log.info("No langfuse prompt is found");
                return mlPromptList;
            }

            for (PromptMeta promptMeta : promptMetas) {
                MLPrompt mlPrompt = MLPrompt.builder().name(promptMeta.getName()).promptManagementType(LANGFUSE).build();
                getPrompt(mlPrompt);
                PromptExtraConfig config = mlPrompt.getPromptExtraConfig();
                config.setAccessKey(this.accessKey);
                config.setPublicKey(this.publicKey);
                mlPrompt.setPromptExtraConfig(config);

                mlPromptList.add(mlPrompt);
            }
            return mlPromptList;
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            if (e instanceof LangfuseClientApiException) {
                errorMessage = getLangfuseClientExceptionMessage((LangfuseClientApiException) e);
            }
            log.error("Failed to import a Langfuse prompt", e);
            throw new OpenSearchStatusException(
                "Failed to import Langfuse Prompts into ML Prompt Index: " + errorMessage,
                RestStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    @Override
    public UpdateDataObjectRequest updatePrompt(MLUpdatePromptInput mlUpdatePromptInput, MLPrompt mlPrompt) {
        getPrompt(mlPrompt);
        MLCreatePromptInput updateContent = MLCreatePromptInput
                .builder()
                .name(mlPrompt.getName())
                .tags(mlPrompt.getTags())
                .prompt(mlPrompt.getPrompt())
                .promptExtraConfig(mlPrompt.getPromptExtraConfig())
                .build();

        // Langfuse does not allow users to change prompt's name
        if (mlUpdatePromptInput.getTags() != null) {
            updateContent.setTags(mlUpdatePromptInput.getTags());
        }
        if (mlUpdatePromptInput.getPrompt() != null) {
            updateContent.setPrompt(mlUpdatePromptInput.getPrompt());
        }
        if (mlUpdatePromptInput.getExtraConfig() != null && mlUpdatePromptInput.getExtraConfig().getLabels() != null) {
            updateContent.getPromptExtraConfig().setLabels(mlUpdatePromptInput.getExtraConfig().getLabels());
        }

        createPrompt(updateContent);
        MLUpdatePromptInput input = MLUpdatePromptInput.builder().build();
        return UpdateDataObjectRequest
            .builder()
            .index(ML_PROMPT_INDEX)
            .id(mlPrompt.getPromptId())
            .tenantId(mlUpdatePromptInput.getTenantId())
            .dataObject(input)
            .build();
    }
}
