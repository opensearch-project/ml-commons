package org.opensearch.ml.common.spi.tools;

/**
 * General parser interface.
 * @param <S>
 * @param <T>
 */
public interface Parser<S, T> {

    /**
     * Parse input.
     * @param input
     * @return
     */
    T parse(S input);
}
