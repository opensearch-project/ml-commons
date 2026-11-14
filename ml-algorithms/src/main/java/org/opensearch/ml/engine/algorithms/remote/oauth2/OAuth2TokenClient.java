/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.oauth2;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE;
import static org.opensearch.ml.engine.algorithms.remote.RemoteConnectorExecutor.RETRY_EXECUTOR;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRunnable;
import org.opensearch.action.support.ThreadedActionListener;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.connector.auth.OAuth2AuthConfig;
import org.opensearch.ml.common.connector.auth.TokenEndpointAllowlist;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.httpclient.TokenEndpointAddressValidator;
import org.opensearch.ml.engine.algorithms.remote.ResolvedAddresses;
import org.opensearch.secure_sm.AccessController;
import org.opensearch.threadpool.ThreadPool;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.log4j.Log4j2;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSource;

/**
 * Requests OAuth2 access tokens with the client credentials grant. A token request goes to the declared token endpoint
 * once its URL is https, in canonical form and on the token-endpoint allowlist, and only to the addresses that passed
 * {@link TokenEndpointAddressValidator}: directly, without redirects, within {@link #TIMEOUT}, reading at most
 * {@link #MAX_RESPONSE_BYTES}. The JVM trust store and hostname verification always apply.
 *
 * <p>Logs one line per request: the host, the status, the outcome and the latency. Tokens, bodies, credential values
 * and exception messages are never logged.
 */
@Log4j2
public final class OAuth2TokenClient {
    public static final int MAX_RESPONSE_BYTES = 64 * 1024;
    public static final Duration TIMEOUT = Duration.ofSeconds(10);
    public static final String FAILURE_MESSAGE = "Failed to obtain OAuth2 token from %s: %s";
    public static final String FAILURE_MESSAGE_WITHOUT_HOST = "Failed to obtain OAuth2 token: %s";
    public static final String TOKEN_URL_NOT_CANONICAL =
        "auth_config.token_url is not in canonical form; write it with a path and without dot segments";
    public static final String HOST_NOT_RESOLVED = "the token endpoint host could not be resolved";
    public static final String IDP_ERROR_DETAIL = "%s (HTTP %d)";
    static final String TRANSPORT_ERROR = "transport_error";

    private final OkHttpClient transport;
    private final TokenEndpointAddressValidator addresses;
    private final ThreadPool threadPool;
    private final BooleanSupplier authEnabled;
    private final IntSupplier maxLifetimeSeconds;

    /**
     * What one successful token request returns; the caller owns {@code token}, which is sanitized printable ASCII.
     * {@code lifetimeSeconds} is already within [1, oauth2_token_cache_max_seconds]; {@code tokenUrl} is the canonical
     * effective URL that passed the allowlist.
     */
    public record IssuedToken(byte[] token, int lifetimeSeconds, String tokenUrl) {
    }

    /**
     * A client on the default OkHttp transport.
     *
     * @param authEnabled whether {@code plugins.ml_commons.connector.auth_enabled} is on, read again before every request
     *     is sent
     * @param maxLifetimeSeconds the longest lifetime a token may be given, read on every request
     */
    public static OAuth2TokenClient create(
        TokenEndpointAddressValidator addresses,
        ThreadPool threadPool,
        BooleanSupplier authEnabled,
        IntSupplier maxLifetimeSeconds
    ) {
        return new OAuth2TokenClient(
            AccessController.doPrivileged(() -> new OkHttpClient()),
            addresses,
            threadPool,
            authEnabled,
            maxLifetimeSeconds
        );
    }

    /** The configuration below is applied to whatever {@code transport} is given. */
    OAuth2TokenClient(
        OkHttpClient transport,
        TokenEndpointAddressValidator addresses,
        ThreadPool threadPool,
        BooleanSupplier authEnabled,
        IntSupplier maxLifetimeSeconds
    ) {
        this.transport = AccessController
            .doPrivileged(
                () -> transport
                    .newBuilder()
                    .proxy(Proxy.NO_PROXY)
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .callTimeout(TIMEOUT)
                    .connectTimeout(TIMEOUT)
                    .readTimeout(TIMEOUT)
                    .writeTimeout(TIMEOUT)
                    .build()
            );
        this.addresses = addresses;
        this.threadPool = threadPool;
        this.authEnabled = authEnabled;
        this.maxLifetimeSeconds = maxLifetimeSeconds;
    }

    /**
     * Requests one token for {@code config}, resolving its references from {@code credentials} and matching the token URL
     * against {@code allowlist}. The checks, the name resolution and the request building run on the
     * {@code opensearch_ml_predict_remote} pool, and the listener completes there too, exactly once; callers restore their
     * own thread context. A request refused before sending fails with an {@link IllegalArgumentException}, with an
     * {@link OpenSearchStatusException} of status 403 when it finds {@code plugins.ml_commons.connector.auth_enabled} off
     * as it is about to be sent, or with an {@link IllegalStateException} when this node's interface addresses cannot be
     * read; a request that was sent and failed, with an {@link MLException}; none has a cause. A full pool fails it with
     * its rejection.
     */
    public void requestToken(
        OAuth2AuthConfig config,
        CredentialSnapshot credentials,
        TokenEndpointAllowlist allowlist,
        ActionListener<IssuedToken> listener
    ) {
        // A completion is short, and it must run even when the pool is full: a rejected completion would drop an issued
        // token or never report a failure.
        ActionListener<IssuedToken> completion = new ThreadedActionListener<>(log, threadPool, RETRY_EXECUTOR, listener, true);
        threadPool.executor(RETRY_EXECUTOR).execute(ActionRunnable.wrap(completion, l -> send(config, credentials, allowlist, l)));
    }

    /** A client that connects only to {@code addresses}, the ones that passed validation, whatever DNS answers next. */
    @VisibleForTesting
    OkHttpClient clientFor(InetAddress[] addresses) {
        return transport.newBuilder().dns(new ResolvedAddresses(addresses)).build();
    }

    private void send(
        OAuth2AuthConfig config,
        CredentialSnapshot credentials,
        TokenEndpointAllowlist allowlist,
        ActionListener<IssuedToken> listener
    ) {
        String canonical;
        try {
            canonical = OAuth2AuthConfig.canonicalTokenUrl(credentials.resolve(config.getTokenUrl()));
        } catch (IllegalArgumentException e) {
            throw refused(null, "not_canonical", e.getMessage());
        }
        // OkHttp resolves dot segments and adds a missing path, so only a URL it leaves unchanged is the one the
        // allowlist matched.
        HttpUrl url = HttpUrl.parse(canonical);
        if (url == null || !url.toString().equals(canonical)) {
            throw refused(null, "not_canonical", TOKEN_URL_NOT_CANONICAL);
        }
        String host = url.host();
        if (!allowlist.matches(canonical)) {
            throw refused(host, "not_allowed", TokenEndpointAllowlist.NOT_ALLOWED_MESSAGE);
        }
        InetAddress[] resolved;
        try {
            resolved = addresses.validate(canonical, host, url.port());
        } catch (UnknownHostException e) {
            throw refused(host, "not_resolved", HOST_NOT_RESOLVED);
        } catch (IllegalStateException e) {
            logRefusal(host, "interfaces_unavailable");
            throw new IllegalStateException(failureMessage(host, e.getMessage()));
        } catch (IllegalArgumentException e) {
            throw refused(host, addressRefusalReason(e.getMessage()), e.getMessage());
        }
        // Read again here: the setting may have been turned off while this request waited for a thread.
        if (!authEnabled.getAsBoolean()) {
            logRefusal(host, "auth_disabled");
            throw new OpenSearchStatusException(ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE, RestStatus.FORBIDDEN);
        }
        Request request = TokenRequests.build(url, config, credentials);
        TokenCallback callback = new TokenCallback(config, canonical, host, listener);
        AccessController.doPrivileged(() -> clientFor(resolved).newCall(request).enqueue(callback));
    }

    private static String addressRefusalReason(String message) {
        return switch (message) {
            case TokenEndpointAddressValidator.REFUSED_PORT_MESSAGE -> "refused_port";
            case TokenEndpointAddressValidator.REFUSED_ADDRESS_MESSAGE -> "refused_address";
            default -> "address_not_allowed";
        };
    }

    /** Logs a request refused before sending; {@code host} is null when the URL did not parse. */
    private static IllegalArgumentException refused(String host, String reason, String detail) {
        logRefusal(host, reason);
        return new IllegalArgumentException(failureMessage(host, detail));
    }

    private static void logRefusal(String host, String reason) {
        if (host == null) {
            log.warn("OAuth2 token request refused before sending: reason={}", reason);
        } else {
            log.warn("OAuth2 token request refused before sending: host={} reason={}", host, reason);
        }
    }

    private static String failureMessage(String host, String detail) {
        return host == null
            ? String.format(Locale.ROOT, FAILURE_MESSAGE_WITHOUT_HOST, detail)
            : String.format(Locale.ROOT, FAILURE_MESSAGE, host, detail);
    }

    /** Runs on an OkHttp thread; completes the listener once. */
    private final class TokenCallback implements Callback {
        private final OAuth2AuthConfig config;
        private final String tokenUrl;
        private final String host;
        private final ActionListener<IssuedToken> listener;
        private final long startedNanos = System.nanoTime();

        TokenCallback(OAuth2AuthConfig config, String tokenUrl, String host, ActionListener<IssuedToken> listener) {
            this.config = config;
            this.tokenUrl = tokenUrl;
            this.host = host;
            this.listener = listener;
        }

        @Override
        public void onResponse(Call call, Response response) {
            byte[] body;
            try (response) {
                body = read(response.body().source());
            } catch (IOException e) {
                onFailure(call, e);
                return;
            }
            int status = response.code();
            IssuedToken token = null;
            String outcome;
            String detail = null;
            try {
                if (response.isSuccessful()) {
                    token = TokenResponses
                        .parse(body, config.getTokenResponse(), tokenUrl, Instant.now().getEpochSecond(), maxLifetimeSeconds.getAsInt());
                    outcome = "ok";
                } else {
                    // Redirects are not followed, so a 3xx is a failure like any other status outside 2xx.
                    outcome = TokenResponses.errorCode(body);
                    detail = String.format(Locale.ROOT, IDP_ERROR_DETAIL, outcome, status);
                }
            } catch (MLException e) {
                outcome = "invalid_response";
                detail = e.getMessage();
            } finally {
                Arrays.fill(body, (byte) 0);
            }
            log.info("OAuth2 token request: host={} status={} outcome={} latency_ms={}", host, status, outcome, latencyMillis());
            if (detail == null) {
                listener.onResponse(token);
            } else {
                listener.onFailure(new MLException(failureMessage(host, detail)));
            }
        }

        @Override
        public void onFailure(Call call, IOException e) {
            log
                .info(
                    "OAuth2 token request: host={} outcome={} latency_ms={} exception={}",
                    host,
                    TRANSPORT_ERROR,
                    latencyMillis(),
                    e.getClass().getSimpleName()
                );
            listener.onFailure(new MLException(failureMessage(host, TRANSPORT_ERROR)));
        }

        private long latencyMillis() {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
        }
    }

    /** At most {@link #MAX_RESPONSE_BYTES} + 1 bytes of the body: enough to tell that a larger body is too large. */
    private static byte[] read(BufferedSource source) throws IOException {
        return source.request(MAX_RESPONSE_BYTES + 1L) ? source.readByteArray(MAX_RESPONSE_BYTES + 1L) : source.getBuffer().readByteArray();
    }
}
