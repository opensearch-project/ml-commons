package org.opensearch.ml.engine.algorithms.remote;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.function.Supplier;

import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.action.bulk.BackoffPolicy;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.algorithms.remote.RemoteConnectorExecutor.RetryableActionExtension;
import org.opensearch.ml.engine.algorithms.remote.RemoteConnectorExecutor.RetryableActionExtensionArgs;
import org.opensearch.threadpool.ThreadPool;

@RunWith(MockitoJUnitRunner.class)
public class RemoteConnectorExecutor_RetryableActionExtensionTest {

    private static final int TEST_ATTEMPT_LIMIT = 5;

    @Mock
    Logger logger;
    @Mock
    ThreadPool threadPool;
    @Mock
    TimeValue initialDelay;
    @Mock
    TimeValue timeoutValue;
    @Mock
    ActionListener<Tuple<Integer, ModelTensors>> listener;
    @Mock
    BackoffPolicy backoffPolicy;
    @Mock
    ConnectorClientConfig connectorClientConfig;
    @Mock
    RemoteConnectorExecutor connectionExecutor;

    RetryableActionExtension retryableAction;

    @Before
    public void setup() {
        when(connectionExecutor.getConnectorClientConfig()).thenReturn(connectorClientConfig);
        when(connectionExecutor.getLogger()).thenReturn(logger);
        var args = RetryableActionExtensionArgs.builder()
            .action("action")
            .connectionExecutor(connectionExecutor)
            .mlInput(mock(MLInput.class))
            .parameters(Map.of())
            .executionContext(mock(ExecutionContext.class))
            .payload("payload")
            .build();
        var settings = Settings.builder().put("node.name", "test").build();
        retryableAction = new RetryableActionExtension(logger, new ThreadPool(settings), TimeValue.timeValueMillis(5), TimeValue.timeValueMillis(500), listener, backoffPolicy, args);
    }

    @Test
    public void test_ShouldRetry_hitLimitOnRetries() {
        var attempts = retryAttempts(-1, this::createThrottleException);

        assertThat(attempts, equalTo(TEST_ATTEMPT_LIMIT));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void test_ShouldRetry_OnlyOnThrottleExceptions() {
        var exceptions = mock(Supplier.class);
        when(exceptions.get())
            .thenReturn(createThrottleException())
            .thenReturn(createThrottleException())
            .thenReturn(new RuntimeException()); // Stop retrying on 3rd exception
        var attempts = retryAttempts(-1, exceptions);

        assertThat(attempts, equalTo(2));
        verify(exceptions, times(3)).get();
    }

    @Test
    public void test_ShouldRetry_stopAtMaxAttempts() {
        int maxAttempts = 3;
        var attempts = retryAttempts(maxAttempts, this::createThrottleException);

        assertThat(attempts, equalTo(maxAttempts));
    }

    private int retryAttempts(int maxAttempts, Supplier<Exception> exception) {
        when(connectorClientConfig.getMaxRetryTimes()).thenReturn(maxAttempts);
        int attempt = 0;
        boolean shouldRetry;
        do {
            shouldRetry = retryableAction.shouldRetry(exception.get());
        } while (shouldRetry && ++attempt < TEST_ATTEMPT_LIMIT);
        return attempt;
    }

    private RemoteConnectorThrottlingException createThrottleException() {
        return new RemoteConnectorThrottlingException("Throttle", RestStatus.TOO_MANY_REQUESTS);
    }
}
