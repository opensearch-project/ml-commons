package org.opensearch.ml.permission;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;

public class AccessControllerTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testGetUserStr() {
        Client client = buildTestClient();

        String userStr = AccessController.getUserStr(client);
        assertEquals("myuser|bckrole1,bckrole2|role1,role2|myTenant", userStr);
    }

    @Test
    public void testGetUser() {
        Client client = buildTestClient();

        User user = AccessController.getUserContext(client);
        assertNotNull(user);
        List<String> backendRoleList = user.getBackendRoles();
        assertEquals(2, backendRoleList.size());
        assertEquals("bckrole1", backendRoleList.get(0));
        assertEquals("bckrole2", backendRoleList.get(1));
    }

    @Test
    public void testCheckUserPermissionsWithNullRequestUser() {
        User resourceUser = User.parse("resourceuser|bckrole1,bckrole2|role1,role2|myTenant");
        boolean hasPermission = AccessController.checkUserPermissions(null, resourceUser, "");
        assertTrue(hasPermission);
    }

    @Test
    public void testCheckUserPermissionsWithNullResourceUser() {
        User requestUser = User.parse("requestuser|bckrole1,bckrole2|role1,role2|myTenant");
        boolean hasPermission = AccessController.checkUserPermissions(requestUser, null, "");
        assertTrue(hasPermission);
    }

    @Test
    public void testCheckUserPermissionsWithNullBackendRoles() {
        User requestUser = User.parse("requestuser||role1,role2|myTenant");
        User resourceUser = User.parse("resourceuser||role1,role2|myTenant");
        boolean hasPermission = AccessController.checkUserPermissions(requestUser, resourceUser, "");
        assertFalse(hasPermission);
    }

    @Test
    public void testCheckUserPermissionsWithoutMatch() {
        User requestUser = User.parse("requestuser|bckrole1,bckrole2|role1,role2|myTenant");
        User resourceUser = User.parse("resourceuser|bckrole3,bckrole4|role1,role2|myTenant");
        boolean hasPermission = AccessController.checkUserPermissions(requestUser, resourceUser, "");
        assertFalse(hasPermission);
    }

    @Test
    public void testCheckUserPermissionsWithMatch() {
        User requestUser = User.parse("requestuser|bckrole1,bckrole2|role1,role2|myTenant");
        User resourceUser = User.parse("resourceuser|bckrole2,bckrole3|role1,role2|myTenant");
        boolean hasPermission = AccessController.checkUserPermissions(requestUser, resourceUser, "");
        assertTrue(hasPermission);
    }

    private Client buildTestClient() {
        Settings settings = Settings.builder().build();
        ThreadContext threadContext = new ThreadContext(settings);
        threadContext
            .putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "myuser|bckrole1,bckrole2|role1,role2|myTenant");
        Client client = mock(Client.class);
        ThreadPool mockThreadPool = mock(ThreadPool.class);
        when(client.threadPool()).thenReturn(mockThreadPool);
        when(mockThreadPool.getThreadContext()).thenReturn(threadContext);
        return client;
    }
}
