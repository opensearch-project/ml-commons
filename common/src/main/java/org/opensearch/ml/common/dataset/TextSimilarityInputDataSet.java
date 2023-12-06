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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.annotation.InputDataSet;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@InputDataSet(MLInputDataType.TEXT_SIMILARITY)
public class TextSimilarityInputDataSet extends MLInputDataset {
    
   List<String> textDocs;

   String queryText;

    @Builder(toBuilder = true)
    public TextSimilarityInputDataSet(String queryText, List<String> textDocs) {
        super(MLInputDataType.TEXT_SIMILARITY);
        Objects.requireNonNull(textDocs);
        Objects.requireNonNull(queryText);
        if(textDocs.isEmpty()) {
            throw new IllegalArgumentException("No text documents provided");
        }
        this.textDocs = textDocs;
        this.queryText = queryText;
    }

    public TextSimilarityInputDataSet(StreamInput in) throws IOException {
        super(MLInputDataType.TEXT_SIMILARITY);
        this.queryText = in.readString();
        int size = in.readInt();
        this.textDocs = new ArrayList<String>();
        for(int i = 0; i < size; i++) {
            String context = in.readString();
            this.textDocs.add(context);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(queryText);
        out.writeInt(this.textDocs.size());
        for (String doc : this.textDocs) {
            out.writeString(doc);
        }
    }
}
