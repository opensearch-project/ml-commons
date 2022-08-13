/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.sentence_transformer;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Arrays;
import java.util.List;

@Data
@AllArgsConstructor
public class STOutput {
    private List<float[]> embedding;

    @Override
    public String toString() {
        StringBuilder embeddingBuilder = new StringBuilder("[\n");
        if (embedding != null ) {
            for (float[] e : embedding) {
                embeddingBuilder.append("    ");
                embeddingBuilder.append(Arrays.toString(e));
                embeddingBuilder.append(",\n");
            }
        }
        embeddingBuilder.append("]");
        return "STOutput{" +
                "embedding=" + embeddingBuilder +
                '}';
    }
}
