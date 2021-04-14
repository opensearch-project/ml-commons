/*
 *  Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License").
 *  You may not use this file except in compliance with the License.
 *  A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package org.opensearch.ml.common.parameter;

import lombok.experimental.UtilityClass;

/**
 * Builder class for MLParameter. All MLParameter objects should be created by this utility.
 */
@UtilityClass
public class MLParameterBuilder {

    public MLParameter parameter(String name, int value) {
        return new MLParameter(name, (Integer) value);
    }

    public MLParameter parameter(String name, int[] value) {
        return new MLParameter(name, value);
    }

    public MLParameter parameter(String name, String value) {
        return new MLParameter(name, value);
    }

    public MLParameter parameter(String name, String[] value) {
        return new MLParameter(name, value);
    }

    public MLParameter parameter(String name, double value) {
        return new MLParameter(name, (Double)value);
    }

    public MLParameter parameter(String name, double[] value) {
        return new MLParameter(name, value);
    }

    public MLParameter parameter(String name, boolean value) {
        return new MLParameter(name, (Boolean)value);
    }

    public MLParameter parameter(String name, boolean[] value) {
        return new MLParameter(name, value);
    }
}
