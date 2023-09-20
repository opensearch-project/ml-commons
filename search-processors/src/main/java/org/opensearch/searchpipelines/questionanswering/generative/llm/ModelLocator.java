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
package org.opensearch.searchpipelines.questionanswering.generative.llm;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.opensearch.client.Client;

/**
 * Helper class for wiring LLMs based on the model ID.
 *
 * TODO Should we extend this use case beyond HttpConnectors/Remote Inference?
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ModelLocator {

    public static Llm getLlm(String modelId, Client client) {

        return new DefaultLlmImpl(modelId, client);
    }

}
