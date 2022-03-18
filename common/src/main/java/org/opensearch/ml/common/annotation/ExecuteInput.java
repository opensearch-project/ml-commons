/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.annotation;

import org.opensearch.ml.common.parameter.FunctionName;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ExecuteInput {
    // supported algorithms. One type of execute input may support multiple algorithms.
    FunctionName[] algorithms();
}
