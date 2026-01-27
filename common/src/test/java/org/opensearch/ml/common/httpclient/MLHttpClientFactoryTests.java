/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.httpclient;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

public class MLHttpClientFactoryTests {

    private static final Logger logger = LogManager.getLogger(MLHttpClientFactory.class);
    private static final String LOG_APPENDER_NAME = "TestLogAppender";
    private static TestLogAppender testAppender;
    private static LoggerConfig loggerConfig;

    @BeforeClass
    public static void setUpClass() {
        testAppender = new TestLogAppender(LOG_APPENDER_NAME);
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        loggerConfig = context.getConfiguration().getLoggerConfig(logger.getName());
        loggerConfig.addAppender(testAppender, Level.WARN, null);
        context.updateLoggers();
    }

    @AfterClass
    public static void tearDownClass() {
        loggerConfig.removeAppender(LOG_APPENDER_NAME);
        testAppender.stop();
    }

    @Test
    public void test_getSdkAsyncHttpClient_success() {
        SdkAsyncHttpClient client = MLHttpClientFactory
            .getAsyncHttpClient(Duration.ofSeconds(100), Duration.ofSeconds(100), 100, false, false);
        assertNotNull(client);
        boolean isWarningLogged = testAppender
            .getLogEvents()
            .stream()
            .anyMatch(
                event -> event.getLevel() == Level.WARN
                    && event
                        .getMessage()
                        .getFormattedMessage()
                        .contains(
                            "SSL certificate verification is DISABLED. This connection is vulnerable to man-in-the-middle"
                                + " attacks. Only use this setting in trusted environments."
                        )
            );
        assertFalse(isWarningLogged);

        client = MLHttpClientFactory.getAsyncHttpClient(Duration.ofSeconds(100), Duration.ofSeconds(100), 100, false, true);
        assertNotNull(client);
        isWarningLogged = testAppender
            .getLogEvents()
            .stream()
            .anyMatch(
                event -> event.getLevel() == Level.WARN
                    && event
                        .getMessage()
                        .getFormattedMessage()
                        .contains(
                            "SSL certificate verification is DISABLED. This connection is vulnerable to man-in-the-middle"
                                + " attacks. Only use this setting in trusted environments."
                        )
            );
        assertTrue(isWarningLogged);
        testAppender.clear();
    }

    /**
     * Log appender class to check the skip ssl verification warning
     */
    static class TestLogAppender extends AbstractAppender {

        private final List<LogEvent> logEvents = new ArrayList<>();

        public TestLogAppender(String name) {
            super(name, null, PatternLayout.createDefaultLayout(), false);
            start();
        }

        @Override
        public void append(LogEvent event) {
            logEvents.add(event.toImmutable());
        }

        public List<LogEvent> getLogEvents() {
            return logEvents;
        }

        public void clear() {
            logEvents.clear();
        }
    }
}
