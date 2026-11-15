/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.oauth2;

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.connector.auth.OAuth2Support;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.secure_sm.AccessController;

import com.google.auth.oauth2.GoogleCredentials;

/**
 * The provider of a legacy {@code google_cloud} connector that uses application default credentials: the identity the
 * node holds (Workload Identity, a metadata server, a credentials file), obtained and refreshed by the Google auth
 * library, which keeps the token. The engine makes no token request of its own for it, so nothing is cached, counted
 * or held back here; the gate is read before every hand-out and by every handle.
 */
public final class GoogleAdcAuthorizationProvider implements AuthorizationProvider {
    /** Begins the message of a token the library could not obtain; what stopped it follows. */
    static final String FAILURE_MESSAGE_PREFIX = "Failed to obtain Google OAuth2 access token";
    private final List<String> scopes;
    private final Gate gate;
    // Built on first use, under the lock accessToken() holds.
    private GoogleCredentials credentials;
    /** Counted up by {@link #invalidate}: a hand-out that finds it moved on refreshes rather than reuses the library's token. */
    private final AtomicInteger invalidations = new AtomicInteger();
    /**
     * The value of {@link #invalidations} the last forced refresh that returned was made for, recorded under the lock. A
     * refresh that fails leaves it as it was, so the next read tries the forced refresh again.
     */
    private int refreshedThrough;

    /**
     * @param scopes the scopes the credentials are created for
     * @param gate the setting the connector runs under, {@code plugins.ml_commons.connector.vertexai_enabled}
     */
    public GoogleAdcAuthorizationProvider(List<String> scopes, Gate gate) {
        this.scopes = scopes;
        this.gate = gate;
    }

    /** A provider over credentials already built. */
    GoogleAdcAuthorizationProvider(GoogleCredentials credentials, Gate gate) {
        this(List.of(), gate);
        this.credentials = credentials;
    }

    @Override
    public void authorizationValue(ActionListener<TokenHandle> listener) {
        OpenSearchStatusException refused = gate.refusal();
        if (refused != null) {
            listener.onFailure(refused);
            return;
        }
        String token;
        try {
            // The library's token is held to what every engine token is: printable ASCII of a bounded length.
            token = new String(OAuth2Support.sanitizeToken(AccessController.doPrivileged(this::accessToken)), US_ASCII);
        } catch (RuntimeException e) {
            listener.onFailure(e);
            return;
        } catch (LinkageError e) {
            // A class the library needs is missing or would not load, beyond the two cases accessToken names: the
            // listener is still completed. Any other Error propagates as everywhere else.
            listener.onFailure(new MLException(FAILURE_MESSAGE_PREFIX + ": " + e, e));
            return;
        }
        listener.onResponse(new TokenHandle(token, null, null, gate));
    }

    /** The library's token is dropped on the next hand-out; the request may be sent again with the one that replaces it. */
    @Override
    public boolean invalidate(TokenHandle handle) {
        invalidations.incrementAndGet();
        return true;
    }

    /**
     * The library's token, refreshed first when {@link #invalidate} was called since the last forced refresh returned. The
     * forced refresh and the reads of the library's token are serialized, so a request sent again after a 401 never reads
     * the token that was rejected. A forced refresh that fails is tried again on the next read, and an invalidation that
     * arrives during a refresh is answered by one more refresh on the read after it rather than dropped. The setting is
     * read again under the lock, so a caller that queued behind a refresh is refused once the setting is off, without a
     * further call into the library.
     */
    private synchronized String accessToken() {
        OpenSearchStatusException refused = gate.refusal();
        if (refused != null) {
            throw refused;
        }
        try {
            GoogleCredentials credentials = credentials();
            int requested = invalidations.get();
            if (requested != refreshedThrough) {
                credentials.refresh();
                refreshedThrough = requested;
            } else {
                credentials.refreshIfExpired();
            }
            return credentials.getAccessToken().getTokenValue();
        } catch (IOException e) {
            throw new MLException(FAILURE_MESSAGE_PREFIX + ": " + e.getMessage(), e);
        } catch (NoClassDefFoundError | ExceptionInInitializerError e) {
            // Resolving application default credentials and refreshing them both reach google-http-client's
            // OpenCensus instrumentation, which needs io.grpc.Context. That class comes from the transport-grpc
            // module, declared optional in plugin/build.gradle. Modules are mandatory in every supported
            // OpenSearch distribution, so this can only fire on an image with modules/ stripped. Catching an
            // Error is deliberate: it is the one chokepoint for token minting, and the raw failure surfaces as
            // an opencensus stack trace naming neither google_cloud nor the missing module.
            throw new MLException("google_cloud token minting requires the transport-grpc module, which is unavailable: " + e, e);
        }
    }

    private GoogleCredentials credentials() {
        if (credentials == null) {
            try {
                credentials = GoogleCredentials.getApplicationDefault().createScoped(scopes);
            } catch (IOException e) {
                throw new MLException("Failed to initialize Google credentials (ADC/Workload Identity): " + e.getMessage(), e);
            }
        }
        return credentials;
    }
}
