package org.opensearch.ml.utils;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class RetryRule implements TestRule {
    private final int retryCount;

    public RetryRule(int retryCount) {
        this.retryCount = retryCount;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Throwable caughtThrowable = null;
                for (int i = 0; i < retryCount; i++) {
                    try {
                        base.evaluate();  // This runs the entire test (setup, test, teardown)
                        return;  // Test passed, exit the loop
                    } catch (Throwable t) {
                        caughtThrowable = t;
                        log.warn("{}: run {} failed", description.getDisplayName(), i + 1);
                    }
                }
                log.error("{}: giving up after {} failures", description.getDisplayName(), retryCount);
                throw caughtThrowable;
            }
        };
    }
}
