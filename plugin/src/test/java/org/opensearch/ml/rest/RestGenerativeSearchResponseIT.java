/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.client.Response;
import org.opensearch.ml.utils.TestHelper;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class RestGenerativeSearchResponseIT extends MLCommonsRestTestCase {

    private static final String RAG_INDEX = "my_rag_test_data";
    private static final String RAG_PIPELINE = "rag_pipeline";
    private static final String SEARCH_TEMPLATE = "simple-rag-template";
    private static final String OPENAI_KEY = System.getenv("OPENAI_KEY");
    private String modelId;

    private final String openaiConnectorEntity =
        """
            {
              "name": "OpenAI Chat Connector",
              "description": "The connector to public OpenAI model service for GPT 3.5",
              "version": 2,
              "protocol": "http",
              "parameters": {
                "endpoint": "api.openai.com",
                "model": "gpt-3.5-turbo",
                "temperature": 0
              },
              "credential": {
                "openAI_key": "%s"
              },
              "actions": [
                {
                  "action_type": "predict",
                  "method": "POST",
                  "url": "https://${parameters.endpoint}/v1/chat/completions",
                  "headers": {
                    "Authorization": "Bearer ${credential.openAI_key}"
                  },
                  "request_body": "{ \\"model\\": \\"${parameters.model}\\", \\"messages\\": ${parameters.messages}, \\"temperature\\": ${parameters.temperature} }"
                }
              ]
            }"""
            .formatted(OPENAI_KEY);

    @Before
    public void setup() throws IOException, InterruptedException {
        if (OPENAI_KEY == null) {
            return;
        }

        // Create OpenAI model
        String openaiModelName = "openAI-gpt-3.5-turbo";
        modelId = registerRemoteModel(openaiConnectorEntity, openaiModelName, true);

        // Create RAG pipeline
        createRagPipeline();

        // Create index with pipeline
        createRagIndex();

        // Create search template
        createSearchTemplate();

        // Ingest test data
        ingestRagTestData();
    }

    @After
    public void cleanup() throws IOException {
        if (OPENAI_KEY == null) {
            return;
        }

        // Delete model
        if (modelId != null) {
            deleteModel(modelId);
        }

        // Delete pipeline
        deletePipeline(RAG_PIPELINE);

        // Delete search template
        deleteSearchTemplate(SEARCH_TEMPLATE);

        // Delete index
        deleteIndex(RAG_INDEX);
    }

    @Test
    public void testGenerativeSearchResponse_WithExtBlock() throws IOException {
        if (OPENAI_KEY == null) {
            return;
        }

        String searchQuery = """
            {
              "query": {
                "match": {
                  "text": "What's the population of NYC metro area in 2023"
                }
              },
              "ext": {
                "generative_qa_parameters": {
                  "llm_model": "gpt-3.5-turbo",
                  "llm_question": "What's the population of NYC metro area in 2023",
                  "context_size": 5,
                  "message_size": 5,
                  "timeout": 15
                }
              }
            }""";

        Response response = TestHelper
            .makeRequest(
                client(),
                "POST",
                "/" + RAG_INDEX + "/_search",
                null,
                new StringEntity(searchQuery),
                List.of(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"))
            );

        String responseBody = TestHelper.httpEntityToString(response.getEntity());
        Map<String, Object> responseMap = gson.fromJson(responseBody, Map.class);

        // Verify response contains hits
        assertTrue("Response should contain hits", responseMap.containsKey("hits"));
        Map<String, Object> hits = (Map<String, Object>) responseMap.get("hits");
        assertTrue("Hits should contain total", hits.containsKey("total"));

        // Verify response contains ext block with retrieval_augmented_generation
        assertTrue("Response should contain ext block", responseMap.containsKey("ext"));
        Map<String, Object> ext = (Map<String, Object>) responseMap.get("ext");
        assertTrue("Ext should contain retrieval_augmented_generation", ext.containsKey("retrieval_augmented_generation"));

        Map<String, Object> rag = (Map<String, Object>) ext.get("retrieval_augmented_generation");
        assertTrue("RAG should contain answer", rag.containsKey("answer"));
        String answer = (String) rag.get("answer");
        assertNotNull("Answer should not be null", answer);
        assertFalse("Answer should not be empty", answer.isEmpty());

    }

    @Test
    public void testGenerativeSearchResponse_WithSearchTemplate() throws IOException {
        if (OPENAI_KEY == null) {
            return;
        }

        String templateQuery = """
            {
              "id": "%s",
              "params": {
                "query_text": "What's the population of NYC metro area in 2023",
                "llm_model": "gpt-3.5-turbo",
                "llm_question": "What's the population of NYC metro area in 2023",
                "context_size": 5,
                "message_size": 5,
                "timeout": 15
              }
            }""".formatted(SEARCH_TEMPLATE);

        Response response = TestHelper
            .makeRequest(
                client(),
                "POST",
                "/" + RAG_INDEX + "/_search/template",
                null,
                new StringEntity(templateQuery),
                List.of(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"))
            );

        String responseBody = TestHelper.httpEntityToString(response.getEntity());
        Map<String, Object> responseMap = gson.fromJson(responseBody, Map.class);

        // Verify response contains hits
        assertTrue("Response should contain hits", responseMap.containsKey("hits"));
        Map<String, Object> hits = (Map<String, Object>) responseMap.get("hits");
        assertTrue("Hits should contain total", hits.containsKey("total"));

        // Verify response contains ext block with retrieval_augmented_generation
        assertTrue("Response should contain ext block", responseMap.containsKey("ext"));
        Map<String, Object> ext = (Map<String, Object>) responseMap.get("ext");
        assertTrue("Ext should contain retrieval_augmented_generation", ext.containsKey("retrieval_augmented_generation"));

        Map<String, Object> rag = (Map<String, Object>) ext.get("retrieval_augmented_generation");
        assertTrue("RAG should contain answer", rag.containsKey("answer"));
        String answer = (String) rag.get("answer");
        assertNotNull("Answer should not be null", answer);
        assertFalse("Answer should not be empty", answer.isEmpty());

    }

    private void createRagPipeline() throws IOException {
        String pipelineBody = """
            {
              "response_processors": [
                {
                  "retrieval_augmented_generation": {
                    "tag": "openai_pipeline_demo",
                    "description": "Demo pipeline Using OpenAI Connector",
                    "model_id": "%s",
                    "context_field_list": ["text"],
                    "system_prompt": "You are a helpful assistant",
                    "user_instructions": "Generate a concise and informative answer in less than 100 words for the given question"
                  }
                }
              ]
            }""".formatted(modelId);

        TestHelper
            .makeRequest(
                client(),
                "PUT",
                "/_search/pipeline/" + RAG_PIPELINE,
                null,
                new StringEntity(pipelineBody),
                List.of(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"))
            );
    }

    private void createRagIndex() throws IOException {
        String indexBody = """
            {
              "settings": {
                "index.search.default_pipeline": "%s"
              },
              "mappings": {
                "properties": {
                  "text": {
                    "type": "text"
                  }
                }
              }
            }""".formatted(RAG_PIPELINE);

        TestHelper
            .makeRequest(
                client(),
                "PUT",
                "/" + RAG_INDEX,
                null,
                new StringEntity(indexBody),
                List.of(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"))
            );
    }

    private void createSearchTemplate() throws IOException {
        String templateBody =
            """
                {
                  "script": {
                    "lang": "mustache",
                    "source": "{\\"query\\":{\\"match\\":{\\"text\\":\\"{{query_text}}\\"}},\\"ext\\":{\\"generative_qa_parameters\\":{\\"llm_model\\":\\"{{llm_model}}\\",\\"llm_question\\":\\"{{llm_question}}\\",\\"context_size\\":{{context_size}},\\"message_size\\":{{message_size}},\\"timeout\\":{{timeout}}}}}"
                  }
                }""";

        TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_scripts/" + SEARCH_TEMPLATE,
                null,
                new StringEntity(templateBody),
                List.of(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"))
            );
    }

    private void ingestRagTestData() throws IOException {
        String bulkRequestBody = "{\"index\":{\"_index\":\""
            + RAG_INDEX
            + "\",\"_id\":\"1\"}}\n"
            + "{\"text\":\"Abraham Lincoln was born on February 12, 1809, the second child of Thomas Lincoln and Nancy Hanks Lincoln, in a log cabin on Sinking Spring Farm near Hodgenville, Kentucky.[2] He was a descendant of Samuel Lincoln, an Englishman who migrated from Hingham, Norfolk, to its namesake, Hingham, Massachusetts, in 1638. The family then migrated west, passing through New Jersey, Pennsylvania, and Virginia.[3] Lincoln was also a descendant of the Harrison family of Virginia; his paternal grandfather and namesake, Captain Abraham Lincoln and wife Bathsheba (née Herring) moved the family from Virginia to Jefferson County, Kentucky.[b] The captain was killed in an Indian raid in 1786.[5] His children, including eight-year-old Thomas, Abraham's father, witnessed the attack.[6][c] Thomas then worked at odd jobs in Kentucky and Tennessee before the family settled in Hardin County, Kentucky, in the early 1800s.\"}\n"
            + "{\"index\":{\"_index\":\""
            + RAG_INDEX
            + "\",\"_id\":\"2\"}}\n"
            + "{\"text\":\"Chart and table of population level and growth rate for the New York City metro area from 1950 to 2023. United Nations population projections are also included through the year 2035.\\nThe current metro area population of New York City in 2023 is 18,937,000, a 0.37% increase from 2022.\\nThe metro area population of New York City in 2022 was 18,867,000, a 0.23% increase from 2021.\\nThe metro area population of New York City in 2021 was 18,823,000, a 0.1% increase from 2020.\\nThe metro area population of New York City in 2020 was 18,804,000, a 0.01% decline from 2019.\"}\n";

        TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_bulk",
                null,
                new StringEntity(bulkRequestBody),
                List.of(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"))
            );

        TestHelper.makeRequest(client(), "POST", "/" + RAG_INDEX + "/_refresh", null, "", List.of());
    }

    private void deletePipeline(String pipelineName) throws IOException {
        try {
            log.info("Deleting pipeline: {}", pipelineName);
            TestHelper.makeRequest(client(), "DELETE", "/_search/pipeline/" + pipelineName, null, "", List.of());
        } catch (Exception e) {
            log.warn("Failed to delete pipeline: {}", pipelineName, e);
        }
    }

    private void deleteSearchTemplate(String templateName) throws IOException {
        try {
            log.info("Deleting search template: {}", templateName);
            TestHelper.makeRequest(client(), "DELETE", "/_scripts/" + templateName, null, "", List.of());
        } catch (Exception e) {
            log.warn("Failed to delete search template: {}", templateName, e);
        }
    }

    private void deleteModel(String modelId) throws IOException {
        try {
            // First try to undeploy the model
            TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/models/" + modelId + "/_undeploy", null, "", List.of());
        } catch (Exception e) {
            log.info("Model {} might not be deployed, continuing with deletion", modelId);
        }

        try {
            // Then delete the model
            log.info("Deleting model: {}", modelId);
            TestHelper.makeRequest(client(), "DELETE", "/_plugins/_ml/models/" + modelId, null, "", List.of());
        } catch (Exception e) {
            log.warn("Failed to delete model: {}", modelId, e);
        }
    }
}
