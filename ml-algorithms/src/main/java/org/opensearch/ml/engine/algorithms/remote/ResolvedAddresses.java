/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import java.net.InetAddress;
import java.util.List;

import lombok.EqualsAndHashCode;
import okhttp3.Dns;

/**
 * The addresses a call may connect to, the ones that passed validation, whatever DNS answers next. Compared by value
 * because OkHttp reuses a pooled connection for a new call only when the two calls' {@link Dns} are equal: the OAuth2
 * token client derives every request's client from one shared transport whose pool serves every token request, so a
 * connection is reused only between requests whose resolved addresses are the same. The streaming handler builds a
 * client per stream, so the comparison is never evaluated there.
 */
@EqualsAndHashCode
public final class ResolvedAddresses implements Dns {
    private final List<InetAddress> addresses;

    public ResolvedAddresses(InetAddress[] addresses) {
        this.addresses = List.of(addresses);
    }

    @Override
    public List<InetAddress> lookup(String hostname) {
        return addresses;
    }
}
