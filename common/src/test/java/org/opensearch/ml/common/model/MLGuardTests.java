/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.search.SearchModule;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

public class MLGuardTests {

    NamedXContentRegistry xContentRegistry;
    @Mock
    Client client;
    @Mock
    SdkClient sdkClient;
    @Mock
    ThreadPool threadPool;
    ThreadContext threadContext;
    String tenantId;

    StopWords stopWords;
    String[] regex;
    List<Pattern> regexPatterns;
    LocalRegexGuardrail inputLocalRegexGuardrail;
    LocalRegexGuardrail outputLocalRegexGuardrail;
    Guardrails guardrails;
    MLGuard mlGuard;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        xContentRegistry = new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents());
        Settings settings = Settings.builder().build();
        this.threadContext = new ThreadContext(settings);
        when(this.client.threadPool()).thenReturn(this.threadPool);
        when(this.threadPool.getThreadContext()).thenReturn(this.threadContext);
        tenantId = "tenantId";

        stopWords = new StopWords("test_index", List.of("test_field").toArray(new String[0]));
        regex = List.of("(.|\n)*stop words(.|\n)*").toArray(new String[0]);
        regexPatterns = List.of(Pattern.compile("(.|\n)*stop words(.|\n)*"));
        inputLocalRegexGuardrail = new LocalRegexGuardrail(List.of(stopWords), regex);
        outputLocalRegexGuardrail = new LocalRegexGuardrail(List.of(stopWords), regex);
        guardrails = new Guardrails("test_type", inputLocalRegexGuardrail, outputLocalRegexGuardrail);
        mlGuard = new MLGuard(guardrails, xContentRegistry, client, sdkClient, tenantId);
    }

    @Test
    public void validateInput() {
        String input = "\n\nHuman:hello stop words.\n\nAssistant:";
        Boolean res = mlGuard.validate(input, MLGuard.Type.INPUT, Collections.emptyMap());

        Assert.assertFalse(res);
    }

    @Test
    public void validateInitializedStopWordsEmpty() {
        stopWords = new StopWords(null, null);
        regex = List.of("(.|\n)*stop words(.|\n)*").toArray(new String[0]);
        regexPatterns = List.of(Pattern.compile("(.|\n)*stop words(.|\n)*"));
        inputLocalRegexGuardrail = new LocalRegexGuardrail(List.of(stopWords), regex);
        outputLocalRegexGuardrail = new LocalRegexGuardrail(List.of(stopWords), regex);
        guardrails = new Guardrails("test_type", inputLocalRegexGuardrail, outputLocalRegexGuardrail);
        mlGuard = new MLGuard(guardrails, xContentRegistry, client, sdkClient, tenantId);

        String input = "\n\nHuman:hello good words.\n\nAssistant:";
        Boolean res = mlGuard.validate(input, MLGuard.Type.INPUT, Collections.emptyMap());
        Assert.assertTrue(res);
    }
}
