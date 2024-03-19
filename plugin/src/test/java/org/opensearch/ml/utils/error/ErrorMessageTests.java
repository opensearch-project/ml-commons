/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils.error;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.opensearch.core.rest.RestStatus.BAD_REQUEST;
import static org.opensearch.core.rest.RestStatus.SERVICE_UNAVAILABLE;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.Test;
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.transport.ActionTransportException;

public class ErrorMessageTests {

    @Test
    public void fetchReason() {
        ErrorMessage errorMessage = new ErrorMessage(new IllegalStateException("illegal state"), SERVICE_UNAVAILABLE.getStatus());

        assertEquals(errorMessage.fetchReason(), "System Error");
    }

    @Test
    public void fetchDetails() {
        ErrorMessage errorMessage = new ErrorMessage(new IllegalStateException("illegal state"), SERVICE_UNAVAILABLE.getStatus());

        assertEquals(errorMessage.fetchDetails(), "illegal state");
    }

    @Test
    public void fetchDetailsWithPrivateIPv4() throws UnknownHostException {
        InetAddress ipAddress = InetAddress.getByName("192.168.1.1");
        Throwable throwable = new ActionTransportException(
            "node 1",
            new TransportAddress(ipAddress, 9300),
            null,
            "Node not connected",
            null
        );
        ErrorMessage errorMessage = new ErrorMessage(throwable, SERVICE_UNAVAILABLE.getStatus());

        assertEquals(throwable.getLocalizedMessage(), "[node 1][192.168.1.1:9300] Node not connected");
        assertEquals(errorMessage.fetchDetails(), "[node 1][x.x.x.x:x] Node not connected");
    }

    @Test
    public void fetchDetailsWithPrivateIPv6_0() throws UnknownHostException {
        InetAddress ipAddress = InetAddress.getByName("0:0:0:0:0:0:0:1");
        Throwable throwable = new ActionTransportException(
            "node 1",
            new TransportAddress(ipAddress, 9300),
            null,
            "Node not connected",
            null
        );
        ErrorMessage errorMessage = new ErrorMessage(throwable, SERVICE_UNAVAILABLE.getStatus());

        assertEquals(throwable.getLocalizedMessage(), "[node 1][[::1]:9300] Node not connected");
        assertEquals(errorMessage.fetchDetails(), "[node 1][x.x.x.x.x.x:x] Node not connected");
    }

    @Test
    public void fetchDetailsWithPrivateIPv6_1() throws UnknownHostException {
        InetAddress ipAddress = InetAddress.getByName("::1");
        Throwable throwable = new ActionTransportException(
            "node 1",
            new TransportAddress(ipAddress, 9300),
            null,
            "Node not connected",
            null
        );
        ErrorMessage errorMessage = new ErrorMessage(throwable, SERVICE_UNAVAILABLE.getStatus());

        assertEquals(throwable.getLocalizedMessage(), "[node 1][[::1]:9300] Node not connected");
        assertEquals(errorMessage.fetchDetails(), "[node 1][x.x.x.x.x.x:x] Node not connected");
    }

    @Test
    public void fetchDetailsWithPrivateIPv61() throws UnknownHostException {
        InetAddress ipAddress = InetAddress.getByName("1234:0000:F560:0000:0000:07C0:89AB:222D");
        Throwable throwable = new ActionTransportException(
            "node 1",
            new TransportAddress(ipAddress, 9300),
            null,
            "Node not connected",
            null
        );
        ErrorMessage errorMessage = new ErrorMessage(throwable, SERVICE_UNAVAILABLE.getStatus());

        assertEquals(throwable.getLocalizedMessage(), "[node 1][[1234:0:f560::7c0:89ab:222d]:9300] Node not connected");
        assertEquals(errorMessage.fetchDetails(), "[node 1][x.x.x.x.x.x:x] Node not connected");
    }

    @Test
    public void testToString() {
        ErrorMessage errorMessage = new ErrorMessage(new IllegalStateException("illegal state"), SERVICE_UNAVAILABLE.getStatus());
        assertEquals(
            "{\"error\":{\"reason\":\"System Error\",\"details\":\"illegal state\",\"type\":\"IllegalStateException\"},\"status\":503}",
            errorMessage.toString()
        );
    }

    @Test
    public void testBadRequestToString() {
        ErrorMessage errorMessage = new ErrorMessage(new IllegalStateException(), BAD_REQUEST.getStatus());
        assertEquals(
            "{\"error\":{\"reason\":\"Invalid Request\",\"details\":\"\",\"type\":\"IllegalStateException\"},\"status\":400}",
            errorMessage.toString()
        );
    }

    @Test
    public void testToStringWithEmptyErrorMessage() {
        ErrorMessage errorMessage = new ErrorMessage(new IllegalStateException(), SERVICE_UNAVAILABLE.getStatus());
        assertEquals(
            "{\"error\":{\"reason\":\"System Error\",\"details\":\"\",\"type\":\"IllegalStateException\"},\"status\":503}",
            errorMessage.toString()
        );
    }

    @Test
    public void getType() {
        ErrorMessage errorMessage = new ErrorMessage(new IllegalStateException("illegal state"), SERVICE_UNAVAILABLE.getStatus());

        assertEquals(errorMessage.getType(), "IllegalStateException");
    }

    @Test
    public void getReason() {
        ErrorMessage errorMessage = new ErrorMessage(new IllegalStateException("illegal state"), SERVICE_UNAVAILABLE.getStatus());

        assertEquals(errorMessage.getReason(), "System Error");
    }

    @Test
    public void getDetails() {
        ErrorMessage errorMessage = new ErrorMessage(new IllegalStateException("illegal state"), SERVICE_UNAVAILABLE.getStatus());

        assertEquals(errorMessage.getDetails(), "illegal state");
    }
}
