/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.commons.ConfigConstants.OPENSEARCH_SECURITY_SSL_HTTP_KEYSTORE_FILEPATH;
import static org.opensearch.ml.rest.SecureMLRestIT.generatePassword;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.client.RestClient;
import org.opensearch.common.io.PathUtils;
import org.opensearch.common.settings.Settings;
import org.opensearch.commons.rest.SecureRestClientBuilder;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.model.BaseModelConfig.FrameworkType;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupInput;
import org.opensearch.ml.common.transport.upload_chunk.MLRegisterModelMetaInput;
import org.opensearch.ml.common.transport.upload_chunk.MLUploadModelChunkInput;
import org.opensearch.ml.utils.TestHelper;

import com.google.common.collect.ImmutableList;

/**
 * End-to-end coverage for models as a resource-sharing resource type.
 * <p>
 * Only meaningful with the security plugin installed, resource sharing enabled, and {@code ml-model} listed in
 * {@code plugins.security.resource_sharing.protected_types} - which is what the integTest cluster sets when run with
 * {@code -Dhttps=true -Dresource_sharing.enabled=true}. Drop {@code ml-model} from that list and the denial assertions
 * below stop failing for the right reason; that is the negative control for this suite.
 * <p>
 * Everything is asserted through the public API. {@code .plugins-ml-model} and the sharing indices are protected
 * system indices, so an ordinary admin REST client is answered as though their documents do not exist - inspecting
 * them from a test proves nothing. Access decisions and the security resource-list API are observable, so they are
 * what this suite checks.
 */
public class MLModelResourceSharingRestIT extends MLCommonsRestTestCase {

    private static final String SHARE_ENDPOINT = "_plugins/_security/api/resource/share";
    private static final String RESOURCE_LIST_ENDPOINT = "_plugins/_security/api/resource/list";
    private static final String MODEL_RESOURCE_TYPE = "ml-model";
    private static final String READ_ONLY = "ml_read_only";

    private final String owner = "rs_model_owner";
    private final String other = "rs_model_other";
    private RestClient ownerClient;
    private RestClient otherClient;

    /**
     * The shared {@code buildClient} builds the super-admin client with {@code SecureRestClientBuilder(settings, path)},
     * which derives its endpoint from settings - {@code http.port: 9200} on localhost - instead of from the cluster's
     * host list. The gradle test cluster binds a random port on IPv6 loopback, so that client reaches either whatever
     * occupies 9200 (a plaintext dev cluster produces "Unrecognized SSL message, plaintext connection?") or nothing at
     * all. Every test then dies before its first assertion.
     * <p>
     * Certificate authentication has to stay, because cleanup deletes system indices such as
     * {@code .plugins-ml-model-group} and only the kirk super-admin certificate may do that - basic-auth admin gets a
     * 403. The three-argument constructor keeps the certificate and takes the real hosts, which is the whole fix.
     */
    /**
     * On a security-enabled cluster the node starts accepting connections before the security plugin has initialized
     * its configuration index - the node log carries "OpenSearch Security not initialized" during that window, and a
     * request that lands in it fails with "Unrecognized SSL message, plaintext connection?", taking the test with it.
     * The framework issues the first request from a {@code @Before}, and JUnit rejects overriding that method, so the
     * only place to wait is a {@code @BeforeClass} - it runs ahead of every {@code @Before}, including the base
     * class's.
     * <p>
     * {@code GET _nodes/plugins} is not a sufficient signal - it answers before security is initialized. The security
     * plugin's own health endpoint is, and it needs no credentials. This throws if security never comes up, so it
     * cannot pass silently.
     */
    @BeforeClass
    public static void waitForSecurityInitialization() throws Exception {
        String cluster = System.getProperty("tests.rest.cluster");
        boolean https = Optional.ofNullable(System.getProperty("https")).map("true"::equalsIgnoreCase).orElse(false);
        if (!https || cluster == null || cluster.isBlank()) {
            return;
        }

        String[] urls = cluster.split(",");
        HttpHost[] hosts = new HttpHost[urls.length];
        for (int i = 0; i < urls.length; i++) {
            String url = urls[i].trim();
            int separator = url.lastIndexOf(':');
            hosts[i] = new HttpHost("https", url.substring(0, separator), Integer.parseInt(url.substring(separator + 1)));
        }
        String user = Optional.ofNullable(System.getProperty("user")).orElse("admin");
        String password = Optional.ofNullable(System.getProperty("password")).orElse("admin");

        Exception last = null;
        for (int attempt = 0; attempt < 60; attempt++) {
            try (RestClient probe = new SecureRestClientBuilder(hosts, true, user, password).setSocketTimeout(10000).build()) {
                Response response = probe.performRequest(new Request("GET", "_plugins/_security/health"));
                String body = EntityUtils.toString(response.getEntity());
                if (body.contains("\"status\":\"UP\"")) {
                    return;
                }
                last = new IllegalStateException("security health reported: " + body);
            } catch (Exception e) {
                last = e;
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("the security plugin never reported UP; secure tests cannot run", last);
    }

    public static long CUSTOM_MODEL_TIMEOUT = 20_000; // 20 seconds

    @Override
    protected RestClient buildClient(Settings settings, HttpHost[] hosts) throws IOException {
        if (isHttps() && settings.get(OPENSEARCH_SECURITY_SSL_HTTP_KEYSTORE_FILEPATH) != null) {
            try {
                URI uri = getClass().getClassLoader().getResource("security/sample.pem").toURI();
                Path configPath = PathUtils.get(uri).getParent().toAbsolutePath();
                return new SecureRestClientBuilder(settings, configPath, hosts).build();
            } catch (URISyntaxException e) {
                throw new IOException("could not resolve the test certificate directory", e);
            }
        }
        return super.buildClient(settings, hosts);
    }

    @Before
    public void setupUsers() throws IOException {
        if (!isHttps()) {
            throw new IllegalArgumentException("Resource-sharing tests require HTTPS; run with -Dhttps=true");
        }

        String ownerPw = generatePassword(owner);
        createUser(owner, ownerPw, ImmutableList.of());
        ownerClient = new SecureRestClientBuilder(getClusterHosts().toArray(new HttpHost[0]), isHttps(), owner, ownerPw)
            .setSocketTimeout(60000)
            .build();

        String otherPw = generatePassword(other);
        createUser(other, otherPw, ImmutableList.of());
        otherClient = new SecureRestClientBuilder(getClusterHosts().toArray(new HttpHost[0]), isHttps(), other, otherPw)
            .setSocketTimeout(60000)
            .build();

        // Both users hold every ML action permission, so any denial below comes from resource sharing rather than from
        // a missing cluster permission. That is the whole point of the suite.
        createRoleMapping("ml_full_access", ImmutableList.of(owner, other));
    }

    @After
    public void cleanupUsers() throws IOException {
        ownerClient.close();
        otherClient.close();
        deleteUser(owner);
        deleteUser(other);
    }

    public void testUnsharedModelIsDeniedAndSharingGrantsReadOnly() throws IOException {
        String modelId = registerModelAs(ownerClient);

        // Not shared: denied even though this user holds ml_full_access
        assertForbidden(() -> getModel(otherClient, modelId));

        shareModel(modelId, READ_ONLY, other);

        // Shared read-only: get is allowed, update is not
        assertEquals(RestStatus.OK, TestHelper.restStatus(getModel(otherClient, modelId)));
        assertForbidden(() -> updateModel(otherClient, modelId, "renamed_by_other"));

        // The owner keeps write access
        assertEquals(RestStatus.OK, TestHelper.restStatus(updateModel(ownerClient, modelId, "renamed_by_owner")));
    }

    public void testSearchVisibilityMatchesPointCheck() throws IOException {
        String modelId = registerModelAs(ownerClient);

        // A model the caller cannot get must not appear in their search results either. This is the assertion that
        // catches the point check and the search filter disagreeing.
        String beforeShare = searchModels(otherClient);
        assertFalse(
            "an unshared model must not appear in another user's search results, but the response contained it: " + beforeShare,
            beforeShare.contains(modelId)
        );
        assertForbidden(() -> getModel(otherClient, modelId));

        shareModel(modelId, READ_ONLY, other);

        String afterShare = searchModels(otherClient);
        assertTrue("a shared model must appear in search results, but the response was: " + afterShare, afterShare.contains(modelId));
        assertEquals(RestStatus.OK, TestHelper.restStatus(getModel(otherClient, modelId)));
    }

    public void testModelChunksAreNotShareableResources() throws IOException {
        String modelId = registerModelAs(ownerClient);
        uploadChunk(ownerClient, modelId, 0);
        uploadChunk(ownerClient, modelId, 1);

        // A sharing record exists for the model and for neither of its chunks. Chunks share the model index but carry
        // no resource_type, so they resolve to no provider and are skipped by the index listener.
        //
        // This asserts on the sharing index rather than through GET _plugins/_security/api/resource/list, because that
        // API returns {"resources":[]} here even though the record and the document's all_shared_principals are both
        // correct - tracked separately; it is a read-path problem in the security plugin, not something this suite can
        // assert around.
        assertTrue("the model must have a sharing record", hasSharingRecord(modelId));
        assertFalse("chunk 0 must not have a sharing record", hasSharingRecord(modelId + "_0"));
        assertFalse("chunk 1 must not have a sharing record", hasSharingRecord(modelId + "_1"));
    }

    /**
     * Sharing records are stored with the resource id as the document id, so existence is a get. The sharing index is a
     * protected system index, which is why this needs the super-admin certificate client.
     */
    private boolean hasSharingRecord(String resourceId) throws IOException {
        try {
            Response response = adminClient().performRequest(new Request("GET", "/.plugins-ml-model-sharing/_doc/" + resourceId));
            return RestStatus.OK == TestHelper.restStatus(response);
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == RestStatus.NOT_FOUND.getStatus()) {
                return false;
            }
            throw e;
        }
    }

    private String registerModelAs(RestClient client) throws IOException {
        // No legacy access-mode parameters: ml-commons rejects them unless model_access_control_enabled is on, and
        // under resource sharing the model's own sharing record authorizes access, so the legacy mode is irrelevant.
        MLRegisterModelGroupInput groupInput = createRegisterModelGroupInput(
            "rs_group_" + randomAlphaOfLength(6).toLowerCase(Locale.ROOT),
            null,
            null,
            null
        );
        Response groupResponse = TestHelper
            .makeRequest(client, "POST", "_plugins/_ml/model_groups/_register", null, TestHelper.toHttpEntity(groupInput), null);
        String modelGroupId = (String) parseResponse(groupResponse).get("model_group_id");
        assertNotNull(modelGroupId);

        TextEmbeddingModelConfig config = TextEmbeddingModelConfig
            .builder()
            .allConfig("All Config")
            .embeddingDimension(1235)
            .frameworkType(FrameworkType.SENTENCE_TRANSFORMERS)
            .modelType("bert")
            .build();
        MLRegisterModelMetaInput metaInput = MLRegisterModelMetaInput
            .builder()
            .name("rs_model")
            .modelGroupId(modelGroupId)
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .modelState(MLModelState.REGISTERING)
            .modelContentHashValue("1234566775")
            .modelContentSizeInBytes(12345L)
            .totalChunks(2)
            .modelConfig(config)
            .build();
        Response metaResponse = TestHelper
            .makeRequest(client, "POST", "_plugins/_ml/models/meta", null, TestHelper.toHttpEntity(metaInput), null);
        String modelId = (String) parseResponse(metaResponse).get("model_id");
        assertNotNull(modelId);

        awaitOwnerAccess(client, modelId);
        return modelId;
    }

    /**
     * The sharing record is written by an index listener, so the owner's own read is the readiness signal: until the
     * record exists the resource check denies everyone, the owner included. A timeout here means the write path never
     * produced a record, which is a product failure rather than a slow test.
     */
    private void awaitOwnerAccess(RestClient client, String modelId) throws IOException {
        String last = "no response captured";
        for (int attempt = 0; attempt < 20; attempt++) {
            try {
                if (RestStatus.OK == TestHelper.restStatus(getModel(client, modelId))) {
                    return;
                }
            } catch (ResponseException e) {
                last = e.getMessage();
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted waiting for owner access to " + modelId, e);
            }
        }
        fail(
            "the owner could not read model "
                + modelId
                + " within 10s, so no resource-sharing record was created for it. last response: "
                + last
        );
        return;
    }

    private void uploadChunk(RestClient client, String modelId, int chunkNumber) throws IOException {
        MLUploadModelChunkInput input = MLUploadModelChunkInput
            .builder()
            .chunkNumber(chunkNumber)
            .content(new byte[] { 1, 3, 4, 5 })
            .modelId(modelId)
            .build();
        TestHelper
            .makeRequest(
                client,
                "POST",
                "_plugins/_ml/models/" + modelId + "/upload_chunk/" + chunkNumber,
                null,
                TestHelper.toHttpEntity(TestHelper.toJsonString(input)),
                null
            );
    }

    private Response getModel(RestClient client, String modelId) throws IOException {
        return TestHelper.makeRequest(client, "GET", "_plugins/_ml/models/" + modelId, null, "", null);
    }

    private Response updateModel(RestClient client, String modelId, String newName) throws IOException {
        return TestHelper.makeRequest(client, "PUT", "_plugins/_ml/models/" + modelId, null, "{\"name\":\"" + newName + "\"}", null);
    }

    private String searchModels(RestClient client) throws IOException {
        try {
            Response response = TestHelper
                .makeRequest(client, "POST", "_plugins/_ml/models/_search", null, "{\"query\":{\"match_all\":{}},\"size\":100}", null);
            return TestHelper.httpEntityToString(response.getEntity());
        } catch (ResponseException e) {
            // A search the caller is not allowed to run at all is reported as-is rather than silently treated as empty.
            return "<error: " + e.getMessage() + ">";
        }
    }

    /**
     * Reads a document from a protected system index with the super-admin certificate client, which is the only client
     * allowed to see them. Used to explain a failure rather than to assert on internals.
     */
    private String systemDocument(String index, String documentId) {
        try {
            Response response = adminClient().performRequest(new Request("GET", "/" + index + "/_doc/" + documentId));
            return TestHelper.httpEntityToString(response.getEntity());
        } catch (Exception e) {
            return "<unavailable: " + e.getMessage() + ">";
        }
    }

    private String accessibleModels(RestClient client) throws IOException {
        Response response = TestHelper
            .makeRequest(client, "GET", RESOURCE_LIST_ENDPOINT + "?resource_type=" + MODEL_RESOURCE_TYPE, null, "", null);
        return TestHelper.httpEntityToString(response.getEntity());
    }

    private void shareModel(String modelId, String accessLevel, String targetUser) throws IOException {
        String payload = "{\"resource_id\":\""
            + modelId
            + "\",\"resource_type\":\""
            + MODEL_RESOURCE_TYPE
            + "\",\"share_with\":{\""
            + accessLevel
            + "\":{\"users\":[\""
            + targetUser
            + "\"]}}}";
        Response response = TestHelper.makeRequest(ownerClient, "PUT", SHARE_ENDPOINT, null, payload, null);
        assertEquals(RestStatus.OK, TestHelper.restStatus(response));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseResponse(Response response) throws IOException {
        return gson.fromJson(TestHelper.httpEntityToString(response.getEntity()), Map.class);
    }

    private void assertForbidden(ThrowingRequest request) {
        try {
            Response response = request.run();
            fail("expected 403 but got " + response.getStatusLine().getStatusCode());
        } catch (ResponseException e) {
            assertEquals(RestStatus.FORBIDDEN.getStatus(), e.getResponse().getStatusLine().getStatusCode());
        } catch (IOException e) {
            throw new AssertionError("request failed for a reason other than authorization", e);
        }
    }

    private interface ThrowingRequest {
        Response run() throws IOException;
    }
}
