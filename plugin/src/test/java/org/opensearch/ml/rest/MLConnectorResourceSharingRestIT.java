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
import org.opensearch.ml.utils.TestHelper;

import com.google.common.collect.ImmutableList;

/**
 * End-to-end coverage for connectors as a resource-sharing resource type.
 * <p>
 * Only meaningful with the security plugin installed, resource sharing enabled, and {@code ml-connector} listed in
 * {@code plugins.security.resource_sharing.protected_types} - which is what the integTest cluster sets when run with
 * {@code -Dhttps=true -Dresource_sharing.enabled=true}. Drop {@code ml-connector} from that list and the denial
 * assertions below stop failing for the right reason; that is the negative control for this suite.
 * <p>
 * The suite exists mainly to hold the access level and the action together: a connector shared at {@code ml_read_only}
 * must be gettable and not updatable, and one shared at {@code ml_read_write} must be both. Authorizing every connector
 * operation against the read action would pass a get-only test and let a read-only recipient update, so the read-only
 * update denial is the assertion that matters here.
 */
public class MLConnectorResourceSharingRestIT extends MLCommonsRestTestCase {

    private static final String SHARE_ENDPOINT = "_plugins/_security/api/resource/share";
    private static final String CONNECTOR_RESOURCE_TYPE = "ml-connector";
    private static final String READ_ONLY = "ml_read_only";
    private static final String READ_WRITE = "ml_read_write";
    private static final String CREDENTIAL_VALUE = "rs_secret_key_value";

    private final String owner = "rs_connector_owner";
    private final String other = "rs_connector_other";
    private RestClient ownerClient;
    private RestClient otherClient;
    private boolean ownerCreated;
    private boolean otherCreated;

    /**
     * See {@link MLModelResourceSharingRestIT#waitForSecurityInitialization()}: the node accepts connections before the
     * security plugin has initialized, and a request that lands in that window fails the whole class.
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

    /**
     * See {@link MLModelResourceSharingRestIT#buildClient(Settings, HttpHost[])}: the shared super-admin client derives
     * its endpoint from settings rather than from the cluster's host list, which does not reach a gradle test cluster.
     */
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
        assumeTypeIsProtected(CONNECTOR_RESOURCE_TYPE);

        String ownerPw = generatePassword(owner);
        createUser(owner, ownerPw, ImmutableList.of());
        ownerCreated = true;
        ownerClient = new SecureRestClientBuilder(getClusterHosts().toArray(new HttpHost[0]), isHttps(), owner, ownerPw)
            .setSocketTimeout(60000)
            .build();

        String otherPw = generatePassword(other);
        createUser(other, otherPw, ImmutableList.of());
        otherCreated = true;
        otherClient = new SecureRestClientBuilder(getClusterHosts().toArray(new HttpHost[0]), isHttps(), other, otherPw)
            .setSocketTimeout(60000)
            .build();

        // Both users hold every ML action permission, so any denial below comes from resource sharing rather than from a
        // missing cluster permission.
        createRoleMapping("ml_full_access", ImmutableList.of(owner, other));
    }

    @After
    public void cleanupUsers() throws IOException {
        // Null-safe so that a failure in setup surfaces its own cause rather than an NPE from cleanup on top of it
        if (ownerClient != null) {
            ownerClient.close();
        }
        if (otherClient != null) {
            otherClient.close();
        }
        // Only users this run created: when the suite skips because the type is not protected, setup returns before
        // creating them, and deleting a user that does not exist fails the whole class on top of the skip.
        if (ownerCreated) {
            deleteUser(owner);
        }
        if (otherCreated) {
            deleteUser(other);
        }
    }

    public void testUnsharedConnectorIsDeniedAndSharingGrantsReadOnly() throws IOException {
        String connectorId = createConnectorAs(ownerClient);

        // Not shared: denied even though this user holds ml_full_access
        assertForbidden(() -> getConnector(otherClient, connectorId));

        shareConnector(connectorId, READ_ONLY, other);

        assertEquals(RestStatus.OK, TestHelper.restStatus(getConnector(otherClient, connectorId)));

        // The access level has to hold per action: read-only must not carry the update action. This fails if every
        // connector operation is authorized against the read action.
        assertForbidden(() -> updateConnector(otherClient, connectorId, "renamed_by_other"));

        // The owner keeps write access
        assertEquals(RestStatus.OK, TestHelper.restStatus(updateConnector(ownerClient, connectorId, "renamed_by_owner")));
    }

    public void testReadWriteShareGrantsUpdateButNotDelete() throws IOException {
        String connectorId = createConnectorAs(ownerClient);

        shareConnector(connectorId, READ_WRITE, other);

        assertEquals(RestStatus.OK, TestHelper.restStatus(getConnector(otherClient, connectorId)));
        assertEquals(RestStatus.OK, TestHelper.restStatus(updateConnector(otherClient, connectorId, "renamed_at_read_write")));

        // ml_read_write lists get, update and execute, so delete stays with the owner and full access
        assertForbidden(() -> deleteConnector(otherClient, connectorId));
        assertEquals(RestStatus.OK, TestHelper.restStatus(deleteConnector(ownerClient, connectorId)));
    }

    public void testSearchVisibilityMatchesPointCheck() throws IOException {
        String connectorId = createConnectorAs(ownerClient);

        // A connector the caller cannot get must not appear in their search results either. This is the assertion that
        // catches the point check and the search filter disagreeing.
        String beforeShare = searchConnectors(otherClient);
        assertFalse(
            "an unshared connector must not appear in another user's search results, but the response contained it: " + beforeShare,
            beforeShare.contains(connectorId)
        );
        assertForbidden(() -> getConnector(otherClient, connectorId));

        shareConnector(connectorId, READ_ONLY, other);

        String afterShare = searchConnectors(otherClient);
        assertTrue(
            "a shared connector must appear in search results, but the response was: " + afterShare,
            afterShare.contains(connectorId)
        );
        assertEquals(RestStatus.OK, TestHelper.restStatus(getConnector(otherClient, connectorId)));
    }

    /**
     * Connector execute is not covered end to end: {@code MLExecuteConnectorAction} has no REST route, so the only way
     * in is the transport action. It is also the one connector operation whose request does not implement
     * {@code DocRequest}, so the plugin's own check is the only thing authorizing it; that the check asks about the
     * action being performed is asserted in {@code ConnectorAccessControlHelperTests}.
     */
    public void testShareRecipientDoesNotSeeCredentials() throws IOException {
        String connectorId = createConnectorAs(ownerClient);
        shareConnector(connectorId, READ_ONLY, other);

        // Sharing grants access to the connector, not to the secret it holds. Asserted on the key's value rather than on
        // the field name, so an encrypted or masked placeholder still passes while a leak does not.
        String body = TestHelper.httpEntityToString(getConnector(otherClient, connectorId).getEntity());
        assertFalse(
            "a share recipient must not receive the connector credential, but the response was: " + body,
            body.contains(CREDENTIAL_VALUE)
        );

        String searchBody = searchConnectors(otherClient);
        assertFalse(
            "connector search must not return credentials, but the response was: " + searchBody,
            searchBody.contains(CREDENTIAL_VALUE)
        );
    }

    private String createConnectorAs(RestClient client) throws IOException {
        String body = "{\"name\":\"rs test connector\","
            + "\"description\":\"a connector used to check resource sharing\","
            + "\"version\":1,"
            + "\"protocol\":\"http\","
            + "\"parameters\":{\"endpoint\":\"api.openai.com\",\"model\":\"gpt-3.5-turbo-instruct\"},"
            + "\"credential\":{\"openAI_key\":\""
            + CREDENTIAL_VALUE
            + "\"},"
            + "\"actions\":[{\"action_type\":\"predict\","
            + "\"method\":\"POST\","
            + "\"url\":\"https://api.openai.com/v1/completions\","
            + "\"headers\":{\"Authorization\":\"Bearer ${credential.openAI_key}\"},"
            + "\"request_body\":\"{\\\"model\\\":\\\"${parameters.model}\\\",\\\"prompt\\\":\\\"${parameters.prompt}\\\"}\"}]}";
        Response response = TestHelper.makeRequest(client, "POST", "_plugins/_ml/connectors/_create", null, body, null);
        String connectorId = (String) parseResponse(response).get("connector_id");
        assertNotNull(connectorId);

        awaitOwnerAccess(client, connectorId);
        return connectorId;
    }

    /**
     * The sharing record is written by an index listener after the create returns, so the owner's own read is the
     * readiness signal: until the record exists the resource check denies everyone, the owner included. A timeout here
     * means the write path never produced a record, which is a product failure rather than a slow test.
     */
    private void awaitOwnerAccess(RestClient client, String connectorId) throws IOException {
        String last = "no response captured";
        for (int attempt = 0; attempt < 20; attempt++) {
            try {
                if (RestStatus.OK == TestHelper.restStatus(getConnector(client, connectorId))) {
                    return;
                }
            } catch (ResponseException e) {
                last = e.getMessage();
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted waiting for owner access to " + connectorId, e);
            }
        }
        fail(
            "the owner could not read connector "
                + connectorId
                + " within 10s, so no resource-sharing record was created for it. last response: "
                + last
        );
    }

    private String searchConnectors(RestClient client) throws IOException {
        try {
            Response response = TestHelper
                .makeRequest(client, "POST", "_plugins/_ml/connectors/_search", null, "{\"query\":{\"match_all\":{}},\"size\":100}", null);
            return TestHelper.httpEntityToString(response.getEntity());
        } catch (ResponseException e) {
            // A search the caller is not allowed to run at all is reported as-is rather than silently treated as empty.
            return "<error: " + e.getMessage() + ">";
        }
    }

    private Response getConnector(RestClient client, String connectorId) throws IOException {
        return TestHelper.makeRequest(client, "GET", "_plugins/_ml/connectors/" + connectorId, null, "", null);
    }

    private Response updateConnector(RestClient client, String connectorId, String newName) throws IOException {
        return TestHelper
            .makeRequest(client, "PUT", "_plugins/_ml/connectors/" + connectorId, null, "{\"name\":\"" + newName + "\"}", null);
    }

    private Response deleteConnector(RestClient client, String connectorId) throws IOException {
        return TestHelper.makeRequest(client, "DELETE", "_plugins/_ml/connectors/" + connectorId, null, "", null);
    }

    private void shareConnector(String connectorId, String accessLevel, String targetUser) throws IOException {
        String payload = "{\"resource_id\":\""
            + connectorId
            + "\",\"resource_type\":\""
            + CONNECTOR_RESOURCE_TYPE
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
            throw new AssertionError("request failed before a status could be read", e);
        }
    }

    @FunctionalInterface
    private interface ThrowingRequest {
        Response run() throws IOException;
    }

    /**
     * These assertions only hold when the type is protected, which the integTest cluster does when run with
     * {@code -Dresource_sharing.enabled=true}. A secure cluster without that flag authorizes by role alone, where a user
     * holding {@code ml_full_access} is allowed everything, so every denial below would fail for a reason that has
     * nothing to do with the code under test. Skip rather than report that as a failure.
     */
    private void assumeTypeIsProtected(String resourceType) throws IOException {
        Response response = TestHelper.makeRequest(client(), "GET", "_nodes/settings?flat_settings=true", null, "", null);
        String settings = TestHelper.httpEntityToString(response.getEntity());
        assumeTrue(
            "resource sharing does not protect " + resourceType + " on this cluster; run with -Dresource_sharing.enabled=true",
            settings.contains(resourceType)
        );
    }
}
