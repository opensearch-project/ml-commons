/*
 * Copyright 2023 Aryn
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensearch.ml.common.dataset;

import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.io.stream.BytesStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

public class TextSimilarityInputDatasetTest {
    
    @Test
    public void testStreaming() throws IOException {
        List<Pair<String, String>> pairs = List.of(
            Pair.of("today is sunny", "That is a happy dog"), 
            Pair.of("today is sunny", "it's summer")
        );
        TextSimilarityInputDataSet dataset = TextSimilarityInputDataSet.builder().pairs(pairs).build();
        BytesStreamOutput outbytes = new BytesStreamOutput();
        StreamOutput osso = new OutputStreamStreamOutput(outbytes);
        dataset.writeTo(osso);
        StreamInput in = new BytesStreamInput(BytesReference.toBytes(outbytes.bytes()));
        TextSimilarityInputDataSet newDs = (TextSimilarityInputDataSet) MLInputDataset.fromStream(in);
        assert (dataset.getPairs().equals(newDs.getPairs()));
    }

    @Test
    public void noPairs_ThenFail() {
        List<Pair<String, String>> pairs = List.of();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, 
            () -> TextSimilarityInputDataSet.builder().pairs(pairs).build());
        assert (e.getMessage().equals("pairs must be nonempty"));
    }
}
