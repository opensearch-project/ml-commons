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

package org.opensearch.ml.stats.suppliers;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * SettableSupplier allows a user to set the value of the supplier to be returned
 */
public class SettableSupplier implements Supplier<Long> {
    protected AtomicLong value;

    /**
     * Constructor
     */
    public SettableSupplier() {
        this.value = new AtomicLong(0L);
    }

    @Override
    public Long get() {
        return value.get();
    }

    /**
     * Set value to be returned by get
     *
     * @param value to set
     */
    public void set(Long value) {
        this.value.set(value);
    }
}
