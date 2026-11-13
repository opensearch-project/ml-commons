/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.oauth2;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_AUTH_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_TRUSTED_OAUTH2_TOKEN_ENDPOINTS_REGEX;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.concurrency.OpenSearchRejectedExecutionException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.connector.auth.TokenEndpointAllowlist;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;

import lombok.extern.log4j.Log4j2;

/**
 * The OAuth2 access tokens of this node, one entry per tenant, {@code auth_config} value and referenced credential
 * values, shared by every connector executor. One token request per entry runs at a time, and requests that find no
 * token wait for it. The first request in the last tenth of a token's lifetime, at most its last minute and a little
 * earlier for each token to spread the refreshes, is served the token and starts a request for the next one. A token is
 * not handed out in its last 5 seconds, or its last twentieth when that is shorter. After a failed token request, which
 * includes one whose token is already past that point when it arrives, requests of the entry that find no token fail
 * without a token request for 1 second, doubling up to 60 seconds, until a token request succeeds or the trusted token
 * endpoints change.
 *
 * <p>Turning {@code plugins.ml_commons.connector.auth_enabled} off removes every token, and a change of
 * {@code plugins.ml_commons.trusted_oauth2_token_endpoints_regex} removes the tokens whose endpoint no longer matches;
 * both are checked again whenever a token is handed out. Token bytes are zero-filled when a token is removed, replaced,
 * evicted or found expired by {@link #removeExpired}. No log line or message carries a token, a credential value, a
 * key, a tenant, an {@code auth_config} value or a token URL.
 */
@Log4j2
public final class OAuth2TokenCache {
    public static final int MAX_ENTRIES = 1000;
    public static final TimeValue EXPIRED_ENTRY_SWEEP_INTERVAL = TimeValue.timeValueSeconds(60);
    public static final String TOKEN_EXPIRED_BEFORE_USE_MESSAGE =
        "The OAuth2 token expired before it could be used: the token request took longer than the token's lifetime";
    static final long MAX_REFRESH_LEAD_NANOS = TimeUnit.SECONDS.toNanos(60);
    static final long MAX_EXPIRY_MARGIN_NANOS = TimeUnit.SECONDS.toNanos(5);
    static final long FIRST_BACKOFF_NANOS = TimeUnit.SECONDS.toNanos(1);
    static final long MAX_BACKOFF_NANOS = TimeUnit.SECONDS.toNanos(60);
    static final long MIN_AGE_BEFORE_INVALIDATION_NANOS = TimeUnit.SECONDS.toNanos(10);

    /** Requests one token. Completes the listener exactly once and does not throw. */
    @FunctionalInterface
    public interface TokenSource {
        void requestToken(
            CredentialSnapshot credentials,
            TokenEndpointAllowlist allowlist,
            ActionListener<OAuth2TokenClient.IssuedToken> listener
        );
    }

    /** What the cache counts. */
    public enum Stat {
        /** A token request was started, for a request that found no token or to refresh one. */
        TOKEN_REQUEST,
        /** A started token request failed; a full thread pool is not counted. */
        TOKEN_FAILURE,
        /** A request was served a cached token. */
        CACHE_HIT,
        /** The remote service answered 401 to a request that carried a token. */
        UNAUTHORIZED_RESPONSE
    }

    // One segment, so the entry evicted beyond MAX_ENTRIES is the least recently used of the whole cache.
    private final Cache<String, Slot> slots = CacheBuilder
        .newBuilder()
        .concurrencyLevel(1)
        .maximumSize(MAX_ENTRIES)
        .removalListener((RemovalNotification<String, Slot> removal) -> removal.getValue().remove())
        .build();
    private final MLFeatureEnabledSetting settings;
    private final BooleanSupplier authEnabled;
    private final Consumer<Stat> stats;
    private final LongSupplier nanoTime;

    /**
     * @param clusterSettings where the cache registers for changes of the connector auth flag and of the trusted token
     *     endpoints
     * @param settings read for the current flag and trusted token endpoints whenever a token is handed out
     * @param stats called once per counted event
     * @param nanoTime the clock of token lifetimes and backoffs
     */
    public OAuth2TokenCache(
        ClusterSettings clusterSettings,
        MLFeatureEnabledSetting settings,
        Consumer<Stat> stats,
        LongSupplier nanoTime
    ) {
        this.settings = settings;
        this.authEnabled = settings::isConnectorAuthEnabled;
        this.stats = stats;
        this.nanoTime = nanoTime;
        clusterSettings.addSettingsUpdateConsumer(ML_COMMONS_CONNECTOR_AUTH_ENABLED, enabled -> {
            if (!enabled) {
                removeAll();
            }
        });
        clusterSettings
            .addSettingsUpdateConsumer(
                ML_COMMONS_TRUSTED_OAUTH2_TOKEN_ENDPOINTS_REGEX,
                regexes -> retainMatching(new TokenEndpointAllowlist(regexes))
            );
    }

    /** The refusal while {@code plugins.ml_commons.connector.auth_enabled} is off. */
    static OpenSearchStatusException authDisabled() {
        return new OpenSearchStatusException(ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE, RestStatus.FORBIDDEN);
    }

    /**
     * Hands {@code listener} the token for one request: the entry's token, or the result of a token request through
     * {@code source}. A request served from the cache completes on the calling thread, one that waits for a token
     * request on the thread that completes it.
     *
     * @param tenantId the connector's tenant; null for the default tenant
     * @param authConfig the connector's {@code auth_config} value, which holds every input of a token request but the
     *     credential values
     * @param credentials read once per call; the values it returns are both part of the key and what a token request
     *     started by this call sends
     */
    public void get(
        String tenantId,
        String authConfig,
        CredentialResolver credentials,
        TokenSource source,
        ActionListener<TokenHandle> listener
    ) {
        if (!authEnabled.getAsBoolean()) {
            listener.onFailure(authDisabled());
            return;
        }
        credentials.snapshot(ActionListener.delegateFailure(listener, (l, snapshot) -> lookUp(tenantId, authConfig, snapshot, source, l)));
    }

    private void lookUp(
        String tenantId,
        String authConfig,
        CredentialSnapshot snapshot,
        TokenSource source,
        ActionListener<TokenHandle> listener
    ) {
        String key = key(tenantId, authConfig, snapshot.fingerprint());
        Slot slot = slots.getIfPresent(key);
        if (slot == null) {
            slot = slots.asMap().computeIfAbsent(key, k -> new Slot());
        }
        TokenHandle hit = null;
        Exception failure = null;
        boolean claimed = false;
        synchronized (slot) {
            long now = nanoTime.getAsLong();
            Token token = slot.token;
            if (token != null && now < token.expiresAtNanos()) {
                if (settings.getTrustedOAuth2TokenEndpoints().matches(token.tokenUrl())) {
                    hit = handle(slot, token);
                    claimed = now >= token.refreshAtNanos() && !slot.fetching && !slot.backingOff(now);
                } else {
                    slot.clearToken();
                    failure = new IllegalArgumentException(TokenEndpointAllowlist.NOT_ALLOWED_MESSAGE);
                }
            } else {
                slot.clearToken();
                if (slot.backingOff(now)) {
                    failure = copyOf(slot.backoff.failure());
                } else {
                    slot.waiters.add(listener);
                    claimed = !slot.fetching;
                }
            }
            if (claimed) {
                slot.fetching = true;
            }
        }
        // Started before the listener runs, so a listener that throws cannot leave the entry waiting for a request that
        // was never sent.
        if (claimed) {
            start(slot, snapshot, source);
        }
        if (hit != null) {
            stats.accept(Stat.CACHE_HIT);
            listener.onResponse(hit);
        } else if (failure != null) {
            listener.onFailure(failure);
        }
    }

    /** Starts the token request of {@code slot}, whose lock the caller does not hold. */
    private void start(Slot slot, CredentialSnapshot snapshot, TokenSource source) {
        // Read before the request is sent, so a token is taken to be as old as it can be.
        long startedAt = nanoTime.getAsLong();
        stats.accept(Stat.TOKEN_REQUEST);
        ActionListener<OAuth2TokenClient.IssuedToken> done = ActionListener
            .wrap(issued -> publish(slot, issued, startedAt), e -> failed(slot, e));
        try {
            source.requestToken(snapshot, settings.getTrustedOAuth2TokenEndpoints(), done);
        } catch (Exception e) {
            // A request that never started must not leave the entry waiting for it.
            done.onFailure(e);
        }
    }

    private void publish(Slot slot, OAuth2TokenClient.IssuedToken issued, long startedAt) {
        Token token = Token.of(issued, startedAt);
        if (token.expiresAtNanos() <= nanoTime.getAsLong()) {
            // Timed from before the request was sent, so the lifetime has already run out; the entry's token, if any, stays.
            Arrays.fill(token.value(), (byte) 0);
            failed(slot, new MLException(TOKEN_EXPIRED_BEFORE_USE_MESSAGE));
            return;
        }
        List<Runnable> completions = new ArrayList<>();
        synchronized (slot) {
            slot.fetching = false;
            List<ActionListener<TokenHandle>> waiters = slot.takeWaiters();
            Supplier<Exception> refusal = null;
            if (!authEnabled.getAsBoolean()) {
                refusal = OAuth2TokenCache::authDisabled;
            } else if (!settings.getTrustedOAuth2TokenEndpoints().matches(issued.tokenUrl())) {
                // The trusted token endpoints changed while the request ran.
                refusal = () -> new IllegalArgumentException(TokenEndpointAllowlist.NOT_ALLOWED_MESSAGE);
            }
            if (refusal != null) {
                Arrays.fill(issued.token(), (byte) 0);
                for (ActionListener<TokenHandle> waiter : waiters) {
                    Supplier<Exception> failure = refusal;
                    completions.add(() -> waiter.onFailure(failure.get()));
                }
            } else {
                for (ActionListener<TokenHandle> waiter : waiters) {
                    TokenHandle handle = handle(slot, token);
                    completions.add(() -> waiter.onResponse(handle));
                }
                if (slot.removed) {
                    // The entry left the cache while the request ran; nothing would zero-fill the token later.
                    Arrays.fill(token.value(), (byte) 0);
                } else {
                    slot.clearToken();
                    slot.token = token;
                    slot.backoff = null;
                }
            }
        }
        completeEach(completions);
    }

    private void failed(Slot slot, Exception e) {
        // A full thread pool says nothing about the token endpoint, which was not asked.
        boolean endpointFailed = !(e instanceof OpenSearchRejectedExecutionException);
        List<ActionListener<TokenHandle>> waiters;
        synchronized (slot) {
            slot.fetching = false;
            waiters = slot.takeWaiters();
            if (endpointFailed) {
                long now = nanoTime.getAsLong();
                slot.backoff = Backoff.after(slot.backoff, e, now);
                log
                    .debug(
                        "OAuth2 token request failed; requests with the same configuration fail without a token request for {} ms",
                        TimeUnit.NANOSECONDS.toMillis(slot.backoff.retryAtNanos() - now)
                    );
            }
        }
        if (endpointFailed) {
            stats.accept(Stat.TOKEN_FAILURE);
        }
        List<Runnable> completions = new ArrayList<>();
        for (ActionListener<TokenHandle> waiter : waiters) {
            completions.add(() -> waiter.onFailure(copyOf(e)));
        }
        completeEach(completions);
    }

    /**
     * Completes the waiters of a token request one by one, so that a listener that throws neither keeps the others
     * waiting nor turns the completed request into a failed one.
     */
    private static void completeEach(List<Runnable> completions) {
        for (Runnable completion : completions) {
            try {
                completion.run();
            } catch (RuntimeException e) {
                log.warn("A listener of an OAuth2 token request failed: {}", e.getClass().getSimpleName());
            }
        }
    }

    /**
     * The remote service answered 401 to a request that carried {@code handle}'s token. Drops the token unless a newer
     * one replaced it or it is younger than ten seconds.
     *
     * @return false only when the token is kept
     */
    public boolean invalidate(TokenHandle handle) {
        stats.accept(Stat.UNAUTHORIZED_RESPONSE);
        Slot slot = handle.slot;
        synchronized (slot) {
            if (slot.token != handle.token) {
                return true;
            }
            if (nanoTime.getAsLong() - handle.token.obtainedAtNanos() < MIN_AGE_BEFORE_INVALIDATION_NANOS) {
                return false;
            }
            slot.clearToken();
            return true;
        }
    }

    /** Zero-fills expired tokens and removes the entries left with no token, no token request and no backoff in effect. */
    public void removeExpired() {
        long now = nanoTime.getAsLong();
        for (Map.Entry<String, Slot> entry : slots.asMap().entrySet()) {
            Slot slot = entry.getValue();
            boolean idle;
            synchronized (slot) {
                if (slot.token != null && now >= slot.token.expiresAtNanos()) {
                    slot.clearToken();
                }
                idle = slot.token == null && !slot.fetching && !slot.backingOff(now);
            }
            // A request that joins the entry before it is removed is still served; its token is not kept.
            if (idle) {
                slots.asMap().remove(entry.getKey(), slot);
            }
        }
    }

    private void removeAll() {
        int removed = clearTokens(token -> true);
        slots.invalidateAll();
        log.info("Connector authentication was disabled; removed {} cached OAuth2 tokens", removed);
    }

    private void retainMatching(TokenEndpointAllowlist allowlist) {
        int removed = clearTokens(token -> !allowlist.matches(token.tokenUrl()));
        log.info("Trusted OAuth2 token endpoints changed; removed {} cached OAuth2 tokens whose token endpoint no longer matches", removed);
    }

    /** Clears the tokens {@code drop} selects and every backoff, each under its entry's lock; returns the tokens cleared. */
    private int clearTokens(Predicate<Token> drop) {
        int cleared = 0;
        for (Slot slot : slots.asMap().values()) {
            synchronized (slot) {
                if (slot.token != null && drop.test(slot.token)) {
                    slot.clearToken();
                    cleared++;
                }
                slot.backoff = null;
            }
        }
        return cleared;
    }

    /** Made under the entry's lock, so the bytes cannot be zero-filled while they are copied. */
    private TokenHandle handle(Slot slot, Token token) {
        return new TokenHandle(new String(token.value(), US_ASCII), slot, token, authEnabled);
    }

    // Listeners may change the exception they receive (GroupedActionListener adds the other failures as suppressed), so
    // callers never share one.
    private static Exception copyOf(Exception e) {
        return switch (e) {
            case IllegalArgumentException refused -> new IllegalArgumentException(refused.getMessage());
            case IllegalStateException unavailable -> new IllegalStateException(unavailable.getMessage());
            case OpenSearchRejectedExecutionException rejected -> new OpenSearchRejectedExecutionException(rejected.getMessage());
            case OpenSearchStatusException refused -> new OpenSearchStatusException(refused.getMessage(), refused.status());
            default -> new MLException(e.getMessage());
        };
    }

    /** The key of an entry: a hash over the three parts, which a JSON array keeps apart. */
    static String key(String tenantId, String authConfig, String credentialFingerprint) {
        List<String> parts = List
            .of(Objects.requireNonNullElse(tenantId, EncryptorImpl.DEFAULT_TENANT_ID), authConfig, credentialFingerprint);
        return StringUtils.hashString(StringUtils.MAPPER.writeValueAsString(parts));
    }

    /** One entry. Every field is guarded by the entry's lock. */
    static final class Slot {
        Token token;
        boolean fetching;
        List<ActionListener<TokenHandle>> waiters = new ArrayList<>();
        Backoff backoff;
        boolean removed;

        boolean backingOff(long now) {
            return backoff != null && now < backoff.retryAtNanos();
        }

        List<ActionListener<TokenHandle>> takeWaiters() {
            List<ActionListener<TokenHandle>> taken = waiters;
            waiters = new ArrayList<>();
            return taken;
        }

        /** Zero-fills the token's bytes and drops it. */
        void clearToken() {
            if (token != null) {
                Arrays.fill(token.value(), (byte) 0);
                token = null;
            }
        }

        /** Called once the entry has left the cache. */
        synchronized void remove() {
            removed = true;
            clearToken();
        }
    }

    /** A cached token; only its bytes change, when they are zero-filled. */
    record Token(byte[] value, long obtainedAtNanos, long refreshAtNanos, long expiresAtNanos, String tokenUrl) {
        static Token of(OAuth2TokenClient.IssuedToken issued, long startedAt) {
            long lifetime = TimeUnit.SECONDS.toNanos(issued.lifetimeSeconds());
            long lead = Math.min(MAX_REFRESH_LEAD_NANOS, lifetime / 10);
            long jitter = lead / 10 == 0 ? 0 : ThreadLocalRandom.current().nextLong(lead / 10);
            long margin = Math.min(MAX_EXPIRY_MARGIN_NANOS, lead / 2);
            return new Token(
                issued.token(),
                startedAt,
                startedAt + lifetime - lead - jitter,
                startedAt + lifetime - margin,
                issued.tokenUrl()
            );
        }
    }

    /** The state of an entry whose last token request failed. */
    private record Backoff(int failures, long retryAtNanos, Exception failure) {
        static Backoff after(Backoff previous, Exception failure, long now) {
            int failures = previous == null ? 1 : previous.failures() + 1;
            long delay = Math.min(MAX_BACKOFF_NANOS, FIRST_BACKOFF_NANOS << Math.min(failures - 1, 6));
            return new Backoff(failures, now + delay, failure);
        }
    }
}
