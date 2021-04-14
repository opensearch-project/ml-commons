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

package org.opensearch.ml.common.dataframe;

import java.io.IOException;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;

import static org.junit.Assert.assertEquals;

public class DoubleValueTest {

    @Test
    public void columnType() {
        assertEquals(ColumnType.DOUBLE, new DoubleValue(2D).columnType());
    }

    @Test
    public void getValue() {
        assertEquals(2D, (Double) new DoubleValue(2.0D).getValue(), 0.00001D);
    }

    @Test
    public void doubleValue() {
        assertEquals(3.0D, new DoubleValue(3.0D).doubleValue(), 0.00001D);
    }

    @Test
    public void writeTo() throws IOException {
        DoubleValue doubleValue = new DoubleValue(5.0D);
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        doubleValue.writeTo(bytesStreamOutput);
        assertEquals(9, bytesStreamOutput.size());
        doubleValue = (DoubleValue) new ColumnValueReader().read(bytesStreamOutput.bytes().streamInput());
        assertEquals(5.0D, doubleValue.doubleValue(), 0.00001D);
    }
}