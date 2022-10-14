/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.exception;

/**
 * Base exception thrown from MLCommons.
 */
public class MLException extends RuntimeException {

    /**
     * Should count this exception in stats or not.
     */
    private boolean countedInStats = true;

    /**
     * Constructor with error message.
     *
     * @param message message of the exception
     */
    public MLException(String message) {
        super(message);
    }

    /**
     * Constructor with specified cause.
     * @param cause exception cause
     */
    public MLException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructor with specified error message adn cause.
     * @param message error message
     * @param cause exception cause
     */
    public MLException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Returns if the exception should be counted in stats.
     *
     * @return true if should count the exception in stats; otherwise return false
     */
    public boolean isCountedInStats() {
        return countedInStats;
    }

    /**
     * Set if the exception should be counted in stats.
     *
     * @param countInStats count the exception in stats
     * @return the exception itself
     */
    public MLException countedInStats(boolean countInStats) {
        this.countedInStats = countInStats;
        return this;
    }
}
