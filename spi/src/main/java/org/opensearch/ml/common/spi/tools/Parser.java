/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.spi.tools;

/**
 * General parser interface.
 * @param <S> The input type
 * @param <T> The return type
 */
public interface Parser<S, T> {

    /**
     * Parse input.
     * @param input the parser input
     * @return output the parser output
     */
    T parse(S input);
}
