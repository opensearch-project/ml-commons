/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.engine;

import lombok.Data;

@Data
public class MLAlgoMetaData {
    private String name;
    private String description;
    private String version;
    private boolean predictable;
    private boolean trainable;

    private MLAlgoMetaData(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.version = builder.version;
        this.predictable = builder.predictable;
        this.trainable = builder.trainable;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String description;
        private String version;
        private boolean predictable;
        private boolean trainable;

        public MLAlgoMetaData build() {
            return new MLAlgoMetaData(this);
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder predictable(boolean predictable) {
            this.predictable = predictable;
            return this;
        }

        public Builder trainable(boolean trainable) {
            this.trainable = trainable;
            return this;
        }
    }
}
