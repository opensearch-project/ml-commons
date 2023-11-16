/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.tools;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MockLLM {

    private static Gson gson = new Gson();

    public static HttpServer setupMockLLM(List<PromptHandler> promptHandlers) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);

        server.createContext("/invoke", exchange -> {
            InputStream ins = exchange.getRequestBody();
            String req = new String(ins.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> map = gson.fromJson(req, Map.class);
            String prompt = map.get("prompt");
            log.debug("prompt received: {}", prompt);

            String llmRes = "";
            for (PromptHandler promptHandler : promptHandlers) {
                if (promptHandler.apply(prompt)) {
                    PromptHandler.LLMResponse llmResponse = new PromptHandler.LLMResponse();
                    llmResponse.setCompletion(promptHandler.response(prompt));
                    llmRes = gson.toJson(llmResponse);
                    break;
                }
            }
            byte[] llmResBytes = llmRes.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, llmResBytes.length);
            exchange.getResponseBody().write(llmResBytes);
            exchange.close();
        });
        return server;
    }
}
