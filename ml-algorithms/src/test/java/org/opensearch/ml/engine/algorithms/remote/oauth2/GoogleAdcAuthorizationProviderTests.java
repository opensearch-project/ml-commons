/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.oauth2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_VERTEXAI_CONNECTOR_DISABLED_MESSAGE;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.OPEN;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.connector.GoogleCloudConnector;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.engine.MLStaticMockBase;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;

public class GoogleAdcAuthorizationProviderTests extends MLStaticMockBase {

    /** The provider completes on the calling thread. */
    private static TokenHandle handle(AuthorizationProvider provider) {
        AtomicReference<TokenHandle> handle = new AtomicReference<>();
        provider.authorizationValue(ActionListener.wrap(handle::set, e -> { throw new AssertionError(e); }));
        return handle.get();
    }

    private static Exception failure(AuthorizationProvider provider) {
        AtomicReference<Exception> failure = new AtomicReference<>();
        provider.authorizationValue(ActionListener.wrap(handle -> fail("served a token"), failure::set));
        return failure.get();
    }

    /**
     * Credentials holding {@code token}, whose refreshes do nothing and are recorded; the token accessor is final, so the
     * library's own is used.
     */
    private static GoogleCredentials credentials(String token) throws IOException {
        GoogleCredentials credentials = spy(GoogleCredentials.create(new AccessToken(token, new Date(Long.MAX_VALUE))));
        doNothing().when(credentials).refresh();
        doNothing().when(credentials).refreshIfExpired();
        return credentials;
    }

    @Test
    public void theCredentialsAreBuiltOnFirstUse_once() {
        // Built before the static mock opens: mocking GoogleCredentials' statics also stubs create().
        GoogleCredentials adc = GoogleCredentials.create(new AccessToken("ya29.adc-token", new Date(Long.MAX_VALUE)));
        GoogleAdcAuthorizationProvider provider = new GoogleAdcAuthorizationProvider(List.of(GoogleCloudConnector.DEFAULT_SCOPE), OPEN);

        try (MockedStatic<GoogleCredentials> mocked = mockStatic(GoogleCredentials.class)) {
            mocked.when(GoogleCredentials::getApplicationDefault).thenReturn(adc);

            assertEquals("ya29.adc-token", handle(provider).value());
            assertEquals("ya29.adc-token", handle(provider).value());

            mocked.verify(GoogleCredentials::getApplicationDefault, times(1));
        }
    }

    @Test
    public void unavailableApplicationDefaultCredentials_failTheRequestNamingTheMode() {
        try (MockedStatic<GoogleCredentials> mocked = mockStatic(GoogleCredentials.class)) {
            mocked.when(GoogleCredentials::getApplicationDefault).thenThrow(new IOException("no ADC available"));

            Exception e = failure(new GoogleAdcAuthorizationProvider(List.of(), OPEN));

            assertSame(MLException.class, e.getClass());
            assertTrue(e.getMessage(), e.getMessage().contains("ADC/Workload Identity"));
        }
    }

    @Test
    public void aMissingModuleWhileTheCredentialsAreBuilt_failsTheRequestNamingTheModule() {
        try (MockedStatic<GoogleCredentials> mocked = mockStatic(GoogleCredentials.class)) {
            mocked.when(GoogleCredentials::getApplicationDefault).thenThrow(new NoClassDefFoundError("io/grpc/Context"));

            Exception e = failure(new GoogleAdcAuthorizationProvider(List.of(), OPEN));

            assertNotNull("the listener was not completed", e);
            assertSame(MLException.class, e.getClass());
            assertTrue(e.getMessage(), e.getMessage().contains("transport-grpc"));
        }
    }

    @Test
    public void aClosedGate_refusesBeforeTheCredentialsAreTouched_andThroughAHandleTakenBefore() throws IOException {
        GoogleCredentials credentials = credentials("t1");
        AtomicBoolean vertexAiEnabled = new AtomicBoolean(true);
        Gate gate = Gate.of(vertexAiEnabled::get, ML_COMMONS_VERTEXAI_CONNECTOR_DISABLED_MESSAGE);
        GoogleAdcAuthorizationProvider provider = new GoogleAdcAuthorizationProvider(credentials, gate);
        TokenHandle before = handle(provider);
        assertEquals("t1", before.value());

        vertexAiEnabled.set(false);

        OpenSearchStatusException refused = (OpenSearchStatusException) failure(provider);
        assertEquals(RestStatus.FORBIDDEN, refused.status());
        assertEquals(ML_COMMONS_VERTEXAI_CONNECTOR_DISABLED_MESSAGE, refused.getMessage());
        assertEquals(
            ML_COMMONS_VERTEXAI_CONNECTOR_DISABLED_MESSAGE,
            assertThrows(OpenSearchStatusException.class, before::value).getMessage()
        );
        verify(credentials, times(1)).refreshIfExpired();

        GoogleCredentials untouched = mock(GoogleCredentials.class);
        failure(new GoogleAdcAuthorizationProvider(untouched, gate));
        verifyNoInteractions(untouched);
    }

    @Test
    public void invalidate_refreshesOnTheNextHandOut_andLetsTheRequestBeSentAgain() throws IOException {
        GoogleCredentials credentials = credentials("t1");
        GoogleAdcAuthorizationProvider provider = new GoogleAdcAuthorizationProvider(credentials, OPEN);
        TokenHandle first = handle(provider);

        assertTrue(provider.invalidate(first));
        assertEquals("t1", handle(provider).value());
        assertEquals("t1", handle(provider).value());

        InOrder order = inOrder(credentials);
        order.verify(credentials).refreshIfExpired();
        order.verify(credentials).refresh();
        order.verify(credentials).refreshIfExpired();
        verify(credentials, times(1)).refresh();
    }

    /** A forced refresh that fails is tried again on the next read; the rejected token is not handed out in between. */
    @Test
    public void aFailedForcedRefresh_isTriedAgainOnTheNextRead_untilItSucceeds() throws IOException {
        AccessToken replacement = new AccessToken("t2", new Date(Long.MAX_VALUE));
        IOException boom = new IOException("boom");
        // The library's own refresh() and refreshIfExpired() run: the rejected token is unexpired, so only a forced
        // refresh replaces it, and a refresh that fails leaves it in place.
        GoogleCredentials credentials = spy(GoogleCredentials.create(new AccessToken("t1", new Date(Long.MAX_VALUE))));
        doThrow(boom).doReturn(replacement).when(credentials).refreshAccessToken();
        GoogleAdcAuthorizationProvider provider = new GoogleAdcAuthorizationProvider(credentials, OPEN);
        TokenHandle rejected = handle(provider);
        assertEquals("t1", rejected.value());

        assertTrue(provider.invalidate(rejected));
        Exception e = failure(provider);
        assertSame(MLException.class, e.getClass());
        assertSame(boom, e.getCause());
        assertEquals("t2", handle(provider).value());
        assertEquals("t2", handle(provider).value());
        verify(credentials, times(2)).refreshAccessToken();
    }

    /** A hand-out that arrives during a forced refresh waits for it and reads the replacement, never the rejected token. */
    @Test
    public void aHandOutDuringAForcedRefresh_waitsForItAndReadsTheReplacement() throws Exception {
        AccessToken replacement = new AccessToken("t2", new Date(Long.MAX_VALUE));
        CountDownLatch refreshing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        // The library's own refresh() and refreshIfExpired() run: the first installs what refreshAccessToken() returns, the
        // second returns at once while the installed token is unexpired.
        GoogleCredentials credentials = spy(GoogleCredentials.create(new AccessToken("t1", new Date(Long.MAX_VALUE))));
        doAnswer(invocation -> {
            refreshing.countDown();
            await(release);
            return replacement;
        }).when(credentials).refreshAccessToken();
        GoogleAdcAuthorizationProvider provider = new GoogleAdcAuthorizationProvider(credentials, OPEN);
        TokenHandle rejected = handle(provider);
        assertEquals("t1", rejected.value());

        AtomicReference<String> refreshed = new AtomicReference<>();
        AtomicReference<String> read = new AtomicReference<>();
        Thread refresher = new Thread(() -> {
            provider.invalidate(rejected);
            refreshed.set(handle(provider).value());
        });
        refresher.start();
        await(refreshing);
        // Arrives while the refresh is in flight: it waits for it and reads the replacement. The refresh is released only
        // once the reader is blocked on the provider's lock; released earlier, a reader scheduled after the refresh had
        // finished would read the replacement without ever having waited, and the serialization would go untested.
        Thread reader = new Thread(() -> read.set(handle(provider).value()));
        reader.start();
        awaitBlocked(reader);
        release.countDown();
        join(refresher);
        join(reader);

        assertEquals("t2", refreshed.get());
        assertEquals("t2", read.get());
        verify(credentials, times(1)).refreshAccessToken();
    }

    /** A caller queued behind a refresh is judged again under the lock: refused, and the library is not called. */
    @Test
    public void aCallerQueuedBehindARefresh_isRefusedOnceTheSettingIsOff_withoutAnotherRefresh() throws Exception {
        AccessToken replacement = new AccessToken("t2", new Date(Long.MAX_VALUE));
        CountDownLatch refreshing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        GoogleCredentials credentials = spy(GoogleCredentials.create(new AccessToken("t1", new Date(Long.MAX_VALUE))));
        doAnswer(invocation -> {
            refreshing.countDown();
            await(release);
            return replacement;
        }).when(credentials).refreshAccessToken();
        AtomicBoolean vertexAiEnabled = new AtomicBoolean(true);
        Gate gate = Gate.of(vertexAiEnabled::get, ML_COMMONS_VERTEXAI_CONNECTOR_DISABLED_MESSAGE);
        GoogleAdcAuthorizationProvider provider = new GoogleAdcAuthorizationProvider(credentials, gate);
        TokenHandle rejected = handle(provider);
        assertEquals("t1", rejected.value());

        AtomicReference<TokenHandle> refreshed = new AtomicReference<>();
        AtomicReference<Exception> queued = new AtomicReference<>();
        Thread refresher = new Thread(() -> {
            provider.invalidate(rejected);
            refreshed.set(handle(provider));
        });
        refresher.start();
        await(refreshing);
        // Passes the check at the entry while the setting is on and queues behind the refresh; the setting goes off
        // while it waits. Once it holds the lock it is refused, and the library is not called for it.
        Thread caller = new Thread(() -> queued.set(failure(provider)));
        caller.start();
        awaitBlocked(caller);
        vertexAiEnabled.set(false);
        release.countDown();
        join(refresher);
        join(caller);

        // The refresh itself completed: its handle holds the replacement, read once the setting is on again.
        vertexAiEnabled.set(true);
        assertEquals("t2", refreshed.get().value());
        OpenSearchStatusException refused = (OpenSearchStatusException) queued.get();
        assertNotNull("served rather than refused", refused);
        assertEquals(RestStatus.FORBIDDEN, refused.status());
        assertEquals(ML_COMMONS_VERTEXAI_CONNECTOR_DISABLED_MESSAGE, refused.getMessage());
        verify(credentials, times(1)).refreshAccessToken();
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertTrue("timed out", latch.await(10, TimeUnit.SECONDS));
    }

    private static void join(Thread thread) throws InterruptedException {
        thread.join(TimeUnit.SECONDS.toMillis(10));
        assertFalse("still running", thread.isAlive());
    }

    private static void awaitBlocked(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (thread.getState() != Thread.State.BLOCKED) {
            if (System.nanoTime() - deadline >= 0) {
                fail("not blocked on the provider's lock: " + thread.getState());
            }
            Thread.onSpinWait();
        }
    }

    @Test
    public void aRefreshFailure_isReportedAsAnMLException_namingTheMissingModuleWhenThatIsTheCause() throws IOException {
        GoogleCredentials failing = mock(GoogleCredentials.class);
        doThrow(new IOException("boom")).when(failing).refreshIfExpired();
        Exception e = failure(new GoogleAdcAuthorizationProvider(failing, OPEN));
        assertSame(MLException.class, e.getClass());
        assertTrue(e.getMessage(), e.getMessage().startsWith(GoogleAdcAuthorizationProvider.FAILURE_MESSAGE_PREFIX));

        // The OAuth2 refresh path reaches google-http-client's OpenCensus instrumentation, which requires io.grpc.Context
        // from the transport-grpc module; on a distribution with modules/ stripped the raw error names neither
        // google_cloud nor the module.
        GoogleCredentials withoutGrpc = mock(GoogleCredentials.class);
        doThrow(new NoClassDefFoundError("io/grpc/Context")).when(withoutGrpc).refreshIfExpired();
        Exception missing = failure(new GoogleAdcAuthorizationProvider(withoutGrpc, OPEN));
        assertSame(MLException.class, missing.getClass());
        assertTrue(missing.getMessage(), missing.getMessage().contains("transport-grpc"));
    }
}
