/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.opensearch.common.regex.Regex;
import org.opensearch.core.rest.RestStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import lombok.SneakyThrows;

/**
 * EmbeddedOcGenaiServer is in-memory mock server for unit test that needs to call OCI services.
 * Ideally we can mock but since we are excluding a bunch of dependencies in OCI SDK common
 * library, let use memory server in UT to gain deeper coverage and avoid missing class runntime
 * exception
 */
public class EmbeddedOciGenaiServer implements Closeable {
    // Runs on ephemeral port
    private static final int PORT = 0;
    private static final String URL = "localhost";

    private static final String SUCCESSFUL_RESPONSE_BODY = "{\n"
        + "    \"generatedTexts\": [\n"
        + "        [\n"
        + "            {\n"
        + "                \"text\": \"answer\"\n"
        + "            }\n"
        + "        ]\n"
        + "    ]\n"
        + "}";

    private static final String FAILED_RESPONSE_BODY = "{\n"
        + "  \"code\" : \"NotAuthorizedOrNotFound\",\n"
        + "  \"message\" : \"Authorization failed or requested resource not found.\"\n"
        + "}";

    private final HttpServer server;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public EmbeddedOciGenaiServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getByName(URL), PORT), 0);
        this.server.createContext("/", new OciHttpHandler());
    }

    public void start() {
        executorService.submit(() -> {
            try {
                server.start();
                Thread.sleep(Integer.MAX_VALUE);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public String getEndpoint() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Override
    public void close() throws IOException {
        server.stop(0);
        executorService.shutdownNow();
    }

    public static class OciHttpHandler implements HttpHandler {
        private static final ObjectMapper MAPPER = new ObjectMapper();

        static {
            // Prevent exceptions from being thrown for unknown properties
            MAPPER.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
            MAPPER.configure(FAIL_ON_IGNORED_PROPERTIES, false);
            MAPPER.setFilterProvider(new SimpleFilterProvider().setFailOnUnknownId(false));
        }

        @Override
        @SneakyThrows
        public void handle(HttpExchange exchange) {
            final String path = exchange.getRequestURI().getPath();
            if (Regex.simpleMatch("/20231130/actions/generateText", path)) {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(RestStatus.OK.getStatus(), 0);
                exchange.getResponseBody().write(SUCCESSFUL_RESPONSE_BODY.getBytes(StandardCharsets.UTF_8));
                exchange.close();
            } else if (Regex.simpleMatch("/20231130/actions/wrongEndpoint", path)) {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(RestStatus.NOT_FOUND.getStatus(), 0);
                exchange.getResponseBody().write(FAILED_RESPONSE_BODY.getBytes(StandardCharsets.UTF_8));
                exchange.close();
            } else {
                throw new RuntimeException(path + " endpoint is supported");
            }
        }
    }
}
