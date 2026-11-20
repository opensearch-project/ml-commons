/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.oauth2;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_AUTH_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_TRUSTED_OAUTH2_TOKEN_ENDPOINTS_REGEX;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.PUBLIC_TOKEN_URL;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.PUBLIC_TOKEN_URL_PATTERN;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.allowNone;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.clientSecret;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.clusterSettings;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.featureSettings;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.snapshot;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TokenCache.MAX_ENTRIES;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TokenCache.Stat.CACHE_HIT;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TokenCache.Stat.TOKEN_FAILURE;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TokenCache.Stat.TOKEN_REQUEST;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TokenCache.Stat.UNAUTHORIZED_RESPONSE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.concurrency.OpenSearchRejectedExecutionException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.connector.auth.TokenEndpointAllowlist;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;

public class OAuth2TokenCacheTests {

    private static final String AUTH = OAuth2TestFixtures.block(PUBLIC_TOKEN_URL, "client_secret_post", "");
    private static final String OTHER_TOKEN_URL = "https://198.51.100.20/token";
    private static final String OTHER_TOKEN_URL_PATTERN = "^https://198\\.51\\.100\\.20/token$";
    private static final String TRANSPORT_FAILURE = String
        .format(Locale.ROOT, OAuth2TokenClient.FAILURE_MESSAGE, OAuth2TestFixtures.PUBLIC_TOKEN_HOST, OAuth2TokenClient.TRANSPORT_ERROR);
    private static final CredentialSnapshot CREDENTIALS = clientSecret();

    private final AtomicLong clock = new AtomicLong();
    private final List<OAuth2TokenCache.Stat> counted = new CopyOnWriteArrayList<>();
    private final RecordingSource source = new RecordingSource();
    private ClusterSettings clusterSettings;
    private MLFeatureEnabledSetting settings;
    private OAuth2TokenCache cache;

    @Before
    public void setUp() {
        clusterSettings = clusterSettings();
        settings = featureSettings(clusterSettings);
        cache = new OAuth2TokenCache(clusterSettings, settings, counted::add, clock::get);
    }

    /** Records every token request; the test completes them. */
    private static final class RecordingSource implements OAuth2TokenCache.TokenSource {
        final List<ActionListener<OAuth2TokenClient.IssuedToken>> requests = new CopyOnWriteArrayList<>();

        @Override
        public void requestToken(
            CredentialSnapshot credentials,
            TokenEndpointAllowlist allowlist,
            ActionListener<OAuth2TokenClient.IssuedToken> listener
        ) {
            requests.add(listener);
        }

        /** Completes the latest request with {@code issued}. */
        OAuth2TokenClient.IssuedToken issue(OAuth2TokenClient.IssuedToken issued) {
            requests.get(requests.size() - 1).onResponse(issued);
            return issued;
        }

        /** Completes the latest request with a one-hour token from {@link OAuth2TestFixtures#PUBLIC_TOKEN_URL}. */
        OAuth2TokenClient.IssuedToken issue(String token) {
            return issue(issued(token, 3600, PUBLIC_TOKEN_URL));
        }

        void fail(Exception e) {
            requests.get(requests.size() - 1).onFailure(e);
        }
    }

    private static OAuth2TokenClient.IssuedToken issued(String token, int lifetimeSeconds, String tokenUrl) {
        return new OAuth2TokenClient.IssuedToken(token.getBytes(US_ASCII), lifetimeSeconds, tokenUrl);
    }

    private void atSeconds(long seconds) {
        clock.set(TimeUnit.SECONDS.toNanos(seconds));
    }

    private void atMillis(long millis) {
        clock.set(TimeUnit.MILLISECONDS.toNanos(millis));
    }

    private CompletableFuture<TokenHandle> get() {
        return get(AUTH);
    }

    private CompletableFuture<TokenHandle> get(String authConfig) {
        return get(null, authConfig, CREDENTIALS);
    }

    private CompletableFuture<TokenHandle> get(String tenantId, String authConfig, CredentialSnapshot credentials) {
        CompletableFuture<TokenHandle> result = new CompletableFuture<>();
        get(tenantId, authConfig, credentials, ActionListener.wrap(result::complete, result::completeExceptionally));
        return result;
    }

    private void get(String tenantId, String authConfig, CredentialSnapshot credentials, ActionListener<TokenHandle> listener) {
        cache.get(tenantId, authConfig, l -> l.onResponse(credentials), source, listener);
    }

    /** The token of the default entry, obtained by a miss and one token request. */
    private TokenHandle obtain(String token) {
        CompletableFuture<TokenHandle> result = get();
        source.issue(token);
        return served(result);
    }

    private TokenHandle obtain(String authConfig, String token, String tokenUrl) {
        CompletableFuture<TokenHandle> result = get(authConfig);
        source.issue(issued(token, 3600, tokenUrl));
        return served(result);
    }

    /** Fills the cache with {@code count} other entries, each waiting for its own token request. */
    private void addOtherEntries(int count) {
        for (int i = 0; i < count; i++) {
            get("other-" + i);
        }
    }

    private void trust(String... patterns) {
        clusterSettings
            .applySettings(Settings.builder().putList(ML_COMMONS_TRUSTED_OAUTH2_TOKEN_ENDPOINTS_REGEX.getKey(), patterns).build());
    }

    private void setAuthEnabled(boolean enabled) {
        clusterSettings.applySettings(Settings.builder().put(ML_COMMONS_CONNECTOR_AUTH_ENABLED.getKey(), enabled).build());
    }

    private static TokenHandle served(CompletableFuture<TokenHandle> result) {
        assertTrue("not completed", result.isDone());
        return result.join();
    }

    private static Exception failure(CompletableFuture<TokenHandle> result) {
        assertTrue("not failed", result.isCompletedExceptionally());
        Exception failure = (Exception) result.handle((handle, e) -> e).join();
        assertNull(failure.getCause());
        return failure;
    }

    private static void assertZeroFilled(byte[] bytes) {
        assertArrayEquals(new byte[bytes.length], bytes);
    }

    private static void assertAuthDisabled(Exception e) {
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) e).status());
        assertEquals(ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE, e.getMessage());
    }

    private static void assertNotAllowed(Exception e) {
        assertTrue(e instanceof IllegalArgumentException);
        assertEquals(TokenEndpointAllowlist.NOT_ALLOWED_MESSAGE, e.getMessage());
    }

    /** A listener whose onResponse throws, as a caller's listener may. */
    private static ActionListener<TokenHandle> throwingListener() {
        return new ActionListener<>() {
            @Override
            public void onResponse(TokenHandle handle) {
                throw new IllegalStateException("listener failed");
            }

            @Override
            public void onFailure(Exception e) {}
        };
    }

    @Test
    public void concurrentMisses_shareOneTokenRequest() throws Exception {
        ExecutorService callers = Executors.newFixedThreadPool(50);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<CompletableFuture<TokenHandle>>> calls = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                calls.add(callers.submit(() -> {
                    go.await();
                    return get();
                }));
            }
            go.countDown();
            List<CompletableFuture<TokenHandle>> results = new ArrayList<>();
            for (Future<CompletableFuture<TokenHandle>> call : calls) {
                results.add(call.get(10, TimeUnit.SECONDS));
            }
            assertEquals(1, source.requests.size());

            source.issue("tok-1");

            OAuth2TokenCache.Token token = served(results.get(0)).token;
            for (CompletableFuture<TokenHandle> result : results) {
                assertSame(token, served(result).token);
                assertEquals("tok-1", served(result).value());
            }
        } finally {
            callers.shutdownNow();
        }
    }

    @Test
    public void theFirstRequestInTheLastTenthOfTheLifetime_isServedAndStartsOneRefresh() {
        obtain("tok-1");

        atSeconds(3533);
        assertEquals("tok-1", served(get()).value());
        assertEquals(1, source.requests.size());

        atSeconds(3540);
        assertEquals("tok-1", served(get()).value());
        assertEquals(2, source.requests.size());
        assertEquals("tok-1", served(get()).value());
        assertEquals(2, source.requests.size());

        atSeconds(3595);
        CompletableFuture<TokenHandle> waiting = get();
        assertFalse(waiting.isDone());
        assertEquals(2, source.requests.size());
        source.issue("tok-2");
        assertEquals("tok-2", served(waiting).value());
    }

    @Test
    public void aOneSecondToken_isServedUntilItsLastTwentieth() {
        CompletableFuture<TokenHandle> first = get();
        source.issue(issued("tok-1", 1, PUBLIC_TOKEN_URL));
        assertEquals("tok-1", served(first).value());

        atMillis(500);
        assertEquals("tok-1", served(get()).value());
        assertEquals(1, source.requests.size());

        atMillis(950);
        assertFalse(get().isDone());
        assertEquals(2, source.requests.size());
    }

    @Test
    public void replacingAToken_zeroFillsItsBytes_whileEarlierHandlesKeepTheirValue() {
        TokenHandle first = obtain("tok-1");
        atSeconds(3540);
        get();
        source.issue("tok-2");

        assertZeroFilled(first.token.value());
        assertEquals("tok-1", first.value());
        assertEquals("tok-2", served(get()).value());
    }

    @Test
    public void evictingTheLeastRecentlyUsedEntry_zeroFillsItsToken() {
        TokenHandle first = obtain("tok-1");

        addOtherEntries(MAX_ENTRIES - 1);
        assertEquals("tok-1", new String(first.token.value(), US_ASCII));
        get("one-more");

        assertZeroFilled(first.token.value());
        assertEquals("tok-1", first.value());
    }

    @Test
    public void invalidateAndRemoveExpired_zeroFillTheTokensTheyDrop() {
        TokenHandle invalidated = obtain("tok-1");
        atSeconds(10);
        assertTrue(cache.invalidate(invalidated));
        assertZeroFilled(invalidated.token.value());
        assertEquals("tok-1", invalidated.value());

        TokenHandle expired = obtain("tok-2");
        atSeconds(10 + 3595);
        cache.removeExpired();
        assertZeroFilled(expired.token.value());
        assertEquals("tok-2", expired.value());
    }

    @Test
    public void invalidate_withAnEarlierTokensHandle_keepsTheNewerToken() {
        TokenHandle first = obtain("tok-1");
        atSeconds(3540);
        get();
        source.issue("tok-2");

        atSeconds(3560);
        assertTrue(cache.invalidate(first));

        assertEquals("tok-2", served(get()).value());
        assertEquals(2, source.requests.size());
    }

    @Test
    public void invalidate_withTheCurrentTokensHandle_dropsTheToken() {
        TokenHandle handle = obtain("tok-1");
        atSeconds(10);

        assertTrue(cache.invalidate(handle));

        assertFalse(get().isDone());
        assertEquals(2, source.requests.size());
    }

    @Test
    public void invalidate_keepsATokenYoungerThanTenSeconds() {
        TokenHandle handle = obtain("tok-1");

        atSeconds(9);
        assertFalse(cache.invalidate(handle));
        assertEquals("tok-1", served(get()).value());
        assertEquals(1, source.requests.size());

        atSeconds(10);
        assertTrue(cache.invalidate(handle));
        assertFalse(get().isDone());
        assertEquals(2, source.requests.size());
    }

    @Test
    public void aTokenRequestedForAnEvictedEntry_servesItsWaitersAndIsNotKept() {
        CompletableFuture<TokenHandle> evictedWaiter = get();
        ActionListener<OAuth2TokenClient.IssuedToken> evictedRequest = source.requests.get(0);
        addOtherEntries(MAX_ENTRIES);
        CompletableFuture<TokenHandle> rebuiltWaiter = get();
        int requests = source.requests.size();

        evictedRequest.onResponse(issued("tok-1", 3600, PUBLIC_TOKEN_URL));

        TokenHandle handle = served(evictedWaiter);
        assertEquals("tok-1", handle.value());
        assertZeroFilled(handle.token.value());
        assertFalse(rebuiltWaiter.isDone());
        assertFalse(get().isDone());
        assertEquals(requests, source.requests.size());
        source.issue("tok-2");
        assertEquals("tok-2", served(rebuiltWaiter).value());
    }

    @Test
    public void afterAFailure_everyWaiterAndEveryFastFailureGetsItsOwnCopy() {
        List<Exception> failures = List
            .of(
                new MLException(TRANSPORT_FAILURE),
                new IllegalArgumentException("refused"),
                new IllegalStateException("unavailable"),
                OAuth2TokenCache.authDisabled()
            );
        for (Exception original : failures) {
            String auth = "auth-" + original.getClass().getSimpleName();
            List<CompletableFuture<TokenHandle>> results = new ArrayList<>(List.of(get(auth), get(auth)));
            source.fail(original);
            atMillis(999);
            for (int i = 0; i < 20; i++) {
                results.add(get(auth));
            }
            atMillis(0);

            Set<Exception> distinct = Collections.newSetFromMap(new IdentityHashMap<>());
            for (CompletableFuture<TokenHandle> result : results) {
                Exception copy = failure(result);
                assertSame(original.getClass(), copy.getClass());
                assertEquals(original.getMessage(), copy.getMessage());
                if (original instanceof OpenSearchStatusException) {
                    assertAuthDisabled(copy);
                }
                assertNotSame(original, copy);
                distinct.add(copy);
            }
            assertEquals(22, distinct.size());
        }
        assertEquals(4, source.requests.size());
    }

    @Test
    public void theBackoffDoublesFromOneSecondUpToSixtySeconds() {
        long nowMillis = 0;
        int requests = 0;
        for (long delaySeconds : new long[] { 1, 2, 4, 8, 16, 32, 60, 60 }) {
            get();
            assertEquals(++requests, source.requests.size());
            source.fail(new MLException(TRANSPORT_FAILURE));

            atMillis(nowMillis + delaySeconds * 1000 - 1);
            failure(get());
            assertEquals(requests, source.requests.size());

            nowMillis += delaySeconds * 1000;
            atMillis(nowMillis);
        }
    }

    @Test
    public void aSuccess_endsTheBackoff() {
        get();
        source.fail(new MLException(TRANSPORT_FAILURE));
        atSeconds(1);
        TokenHandle handle = obtain("tok-1");

        atSeconds(11);
        assertTrue(cache.invalidate(handle));
        get();
        source.fail(new MLException(TRANSPORT_FAILURE));
        atSeconds(12);
        assertFalse(get().isDone());
        assertEquals(4, source.requests.size());
    }

    @Test
    public void aFailedRefresh_keepsTheTokenServedUntilItExpires_thenRequestsFailFast() {
        obtain("tok-1");
        atSeconds(3540);
        assertEquals("tok-1", served(get()).value());
        source.fail(new MLException(TRANSPORT_FAILURE));

        atMillis(3594_500);
        assertEquals("tok-1", served(get()).value());
        assertEquals(3, source.requests.size());
        source.fail(new MLException(TRANSPORT_FAILURE));

        atSeconds(3595);
        assertEquals(TRANSPORT_FAILURE, failure(get()).getMessage());
        assertEquals(3, source.requests.size());
    }

    @Test
    public void aTokenAlreadyExpiredWhenItArrives_isAFailedRequest() {
        CompletableFuture<TokenHandle> waiter = get();
        atMillis(950);

        OAuth2TokenClient.IssuedToken issued = source.issue(issued("tok-1", 1, PUBLIC_TOKEN_URL));

        Exception failure = failure(waiter);
        assertSame(MLException.class, failure.getClass());
        assertEquals(OAuth2TokenCache.TOKEN_EXPIRED_BEFORE_USE_MESSAGE, failure.getMessage());
        assertZeroFilled(issued.token());
        assertEquals(OAuth2TokenCache.TOKEN_EXPIRED_BEFORE_USE_MESSAGE, failure(get()).getMessage());
        assertEquals(1, source.requests.size());
        assertEquals(List.of(TOKEN_REQUEST, TOKEN_FAILURE), counted);
        atMillis(1950);
        assertFalse(get().isDone());
        assertEquals(2, source.requests.size());
    }

    @Test
    public void aRefreshAlreadyExpiredWhenItArrives_keepsTheTokenItWouldHaveReplaced() {
        TokenHandle first = obtain("tok-1");
        atSeconds(3540);
        assertEquals("tok-1", served(get()).value());
        atSeconds(3541);

        source.issue(issued("tok-2", 1, PUBLIC_TOKEN_URL));

        TokenHandle kept = served(get());
        assertEquals("tok-1", kept.value());
        assertSame(first.token, kept.token);
        assertEquals(2, source.requests.size());
    }

    @Test
    public void aFullThreadPool_failsTheWaiters_withoutABackoffOrAFailureCount() {
        CompletableFuture<TokenHandle> waiter = get();
        OpenSearchRejectedExecutionException rejection = new OpenSearchRejectedExecutionException("rejected");
        source.fail(rejection);

        Exception failure = failure(waiter);
        assertSame(OpenSearchRejectedExecutionException.class, failure.getClass());
        assertNotSame(rejection, failure);
        assertFalse(get().isDone());
        assertEquals(2, source.requests.size());
        assertEquals(List.of(TOKEN_REQUEST, TOKEN_REQUEST), counted);
    }

    @Test
    public void whileAuthIsOff_noCredentialIsReadAndNoTokenRequested() {
        setAuthEnabled(false);
        AtomicInteger reads = new AtomicInteger();
        CompletableFuture<TokenHandle> result = new CompletableFuture<>();

        cache.get(null, AUTH, listener -> {
            reads.incrementAndGet();
            listener.onResponse(CREDENTIALS);
        }, source, ActionListener.wrap(result::complete, result::completeExceptionally));

        assertAuthDisabled(failure(result));
        assertEquals(0, reads.get());
        assertTrue(source.requests.isEmpty());
    }

    @Test
    public void turningAuthOff_zeroFillsEveryToken_andHandlesTakenBeforeRefuseTheirValue() {
        TokenHandle handle = obtain("tok-1");

        setAuthEnabled(false);

        assertZeroFilled(handle.token.value());
        assertAuthDisabled(assertThrows(OpenSearchStatusException.class, handle::value));
        setAuthEnabled(true);
        assertFalse(get().isDone());
        assertEquals(2, source.requests.size());
    }

    @Test
    public void aTokenIssuedAfterAuthTurnedOff_failsItsWaitersAndIsNotKept() {
        CompletableFuture<TokenHandle> waiter = get();
        setAuthEnabled(false);

        OAuth2TokenClient.IssuedToken issued = source.issue("tok-1");

        assertAuthDisabled(failure(waiter));
        assertZeroFilled(issued.token());
        setAuthEnabled(true);
        assertFalse(get().isDone());
        assertEquals(2, source.requests.size());
    }

    @Test
    public void changingTheTrustedEndpoints_dropsTheTokensThatNoLongerMatch_andEveryBackoff() {
        trust(PUBLIC_TOKEN_URL_PATTERN, OTHER_TOKEN_URL_PATTERN);
        TokenHandle dropped = obtain("auth-a", "tok-a", PUBLIC_TOKEN_URL);
        obtain("auth-b", "tok-b", OTHER_TOKEN_URL);
        get("auth-c");
        source.fail(new MLException(TRANSPORT_FAILURE));
        int requests = source.requests.size();

        trust(OTHER_TOKEN_URL_PATTERN);

        assertZeroFilled(dropped.token.value());
        assertEquals("tok-a", dropped.value());
        assertEquals("tok-b", served(get("auth-b")).value());
        assertFalse(get("auth-a").isDone());
        assertFalse(get("auth-c").isDone());
        assertEquals(requests + 2, source.requests.size());
    }

    @Test
    public void aTokenRequestedUnderAnEarlierList_isHandedOutOnlyIfItStillMatches() {
        CompletableFuture<TokenHandle> stillTrusted = get("auth-a");
        trust(PUBLIC_TOKEN_URL_PATTERN, OTHER_TOKEN_URL_PATTERN);
        source.issue("tok-a");
        assertEquals("tok-a", served(stillTrusted).value());
        assertEquals("tok-a", served(get("auth-a")).value());

        CompletableFuture<TokenHandle> noLongerTrusted = get("auth-b");
        trust(OTHER_TOKEN_URL_PATTERN);
        source.issue("tok-b");
        assertNotAllowed(failure(noLongerTrusted));
        assertFalse(get("auth-b").isDone());
        assertEquals(3, source.requests.size());
    }

    @Test
    public void aRefreshRequestedUnderAnEarlierList_isNotHandedOut() {
        obtain("tok-1");
        atSeconds(3540);
        get();
        trust(OTHER_TOKEN_URL_PATTERN);
        CompletableFuture<TokenHandle> waiter = get();

        source.issue("tok-2");

        assertNotAllowed(failure(waiter));
        assertEquals(2, source.requests.size());
    }

    @Test
    public void aCachedToken_isMatchedAgainstTheCurrentListWhenHandedOut() {
        TokenHandle handle = obtain("tok-1");
        when(settings.getTrustedOAuth2TokenEndpoints()).thenReturn(allowNone());

        assertNotAllowed(failure(get()));

        assertZeroFilled(handle.token.value());
        assertEquals("tok-1", handle.value());
    }

    @Test
    public void entriesAreKeptApartByTenantConfigurationAndCredentialValues() {
        obtain("tok-1");

        assertEquals("tok-1", served(get(EncryptorImpl.DEFAULT_TENANT_ID, AUTH, CREDENTIALS)).value());
        assertEquals(1, source.requests.size());
        get("tenant-a", AUTH, CREDENTIALS);
        get("tenant-b", AUTH, CREDENTIALS);
        get(null, AUTH.replace("}", ",\"scope\":\"other\"}"), CREDENTIALS);
        CompletableFuture<TokenHandle> copied = get(null, AUTH, snapshot(Map.of("client_secret", "made-up")));

        assertFalse(copied.isDone());
        assertEquals(5, source.requests.size());
    }

    @Test
    public void key_keepsItsPartsApart() {
        assertNotEquals(OAuth2TokenCache.key("ab", "c", "f"), OAuth2TokenCache.key("a", "bc", "f"));
        assertNotEquals(OAuth2TokenCache.key("t", "a", "b"), OAuth2TokenCache.key("t", "ab", ""));
    }

    @Test
    public void removeExpired_dropsExpiredTokensAndIdleEntries_andKeepsEntriesWithARequestOrABackoff() {
        TokenHandle expired = obtain("auth-expired", "tok-1", PUBLIC_TOKEN_URL);
        atSeconds(3595);
        CompletableFuture<TokenHandle> fetching = get("auth-fetching");
        ActionListener<OAuth2TokenClient.IssuedToken> fetchingRequest = source.requests.get(1);
        get("auth-failing");
        source.fail(new MLException(TRANSPORT_FAILURE));

        cache.removeExpired();

        assertZeroFilled(expired.token.value());
        assertTrue(expired.slot.removed);
        failure(get("auth-failing"));
        fetchingRequest.onResponse(issued("tok-2", 3600, PUBLIC_TOKEN_URL));
        assertEquals("tok-2", served(fetching).value());
        assertEquals("tok-2", served(get("auth-fetching")).value());
        assertEquals(3, source.requests.size());
    }

    @Test
    public void eachStatCountsOnceOnItsPath_andAFastFailureInNone() {
        CompletableFuture<TokenHandle> miss = get();
        assertEquals(List.of(TOKEN_REQUEST), counted);
        source.issue("tok-1");
        served(miss);
        assertEquals(List.of(TOKEN_REQUEST), counted);

        TokenHandle hit = served(get());
        atSeconds(10);
        cache.invalidate(hit);
        get();
        source.fail(new MLException(TRANSPORT_FAILURE));
        failure(get());

        assertEquals(List.of(TOKEN_REQUEST, CACHE_HIT, UNAUTHORIZED_RESPONSE, TOKEN_REQUEST, TOKEN_FAILURE), counted);
    }

    @Test
    public void aSourceThatThrows_failsItsWaiters_andALaterRequestStartsAgain() {
        CompletableFuture<TokenHandle> result = new CompletableFuture<>();
        cache.get(null, AUTH, listener -> listener.onResponse(CREDENTIALS), (credentials, allowlist, listener) -> {
            throw new IllegalStateException("source failed");
        }, ActionListener.wrap(result::complete, result::completeExceptionally));

        assertEquals("source failed", failure(result).getMessage());
        atSeconds(1);
        assertFalse(get().isDone());
        assertEquals(1, source.requests.size());
    }

    @Test
    public void aListenerThatThrowsOnARefreshHit_doesNotStopTheRefreshItStarted() {
        obtain("tok-1");
        atSeconds(3540);

        assertThrows(IllegalStateException.class, () -> get(null, AUTH, CREDENTIALS, throwingListener()));

        assertEquals(2, source.requests.size());
        source.issue("tok-2");
        atSeconds(3600);
        assertEquals("tok-2", served(get()).value());
    }

    @Test
    public void aWaiterThatThrows_doesNotKeepTheOtherWaitersWaiting() {
        get(null, AUTH, CREDENTIALS, throwingListener());
        CompletableFuture<TokenHandle> other = get();

        source.issue("tok-1");

        assertEquals("tok-1", served(other).value());
        assertEquals("tok-1", served(get()).value());
        assertEquals(1, source.requests.size());
        assertFalse(counted.contains(TOKEN_FAILURE));
    }
}
