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

package org.opensearch.ml.engine;

import org.opensearch.ml.common.dataframe.DataFrame;

import java.io.*;
import java.util.Base64;

public abstract class AbstractMLAlgo implements MLAlgo {
    @Override
    public abstract DataFrame predict(DataFrame dataFrame, String model);

    @Override
    public abstract String train(DataFrame dataFrame);

    public String modelToString(Object model) throws IOException {

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(model);
        objectOutputStream.flush();
        String res = Base64.getEncoder().encodeToString(byteArrayOutputStream.toByteArray());
        objectOutputStream.close();
        byteArrayOutputStream.close();

        return res;
    }

    public Object stringToModel(String model) throws IOException, ClassNotFoundException {

        byte[] modelBin = Base64.getDecoder().decode(model);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(modelBin);
        ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
        Object res = objectInputStream.readObject();
        objectInputStream.close();
        inputStream.close();

        return res;
    }
}
