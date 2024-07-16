/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.opensearch.ml.common.dataset.MLInputDataType;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface InputDataSet {
    MLInputDataType value();
}
