/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.helper;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.math3.distribution.MultivariateNormalDistribution;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.dataframe.ColumnMeta;
import org.opensearch.ml.common.dataframe.ColumnType;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.engine.encryptor.Encryptor;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class MLTestHelper {

    public static final String TIME_FIELD = "timestamp";

    public static DataFrameInputDataset concstructDataFrameInputDataSet(int size) {
        return new DataFrameInputDataset(constructTestDataFrame(size));
    }

    public static DataFrame constructTestDataFrame(int size) {
        return constructTestDataFrame(size, false);
    }

    public static DataFrame constructTestDataFrame(int size, boolean addTimeFiled) {
        List<ColumnMeta> columnMetaList = new ArrayList<>();
        columnMetaList.add(new ColumnMeta("f1", ColumnType.DOUBLE));
        columnMetaList.add(new ColumnMeta("f2", ColumnType.DOUBLE));
        if (addTimeFiled) {
            columnMetaList.add(new ColumnMeta(TIME_FIELD, ColumnType.LONG));
        }
        ColumnMeta[] columnMetas = columnMetaList.toArray(new ColumnMeta[0]);
        DataFrame dataFrame = DataFrameBuilder.emptyDataFrame(columnMetas);

        Random random = new Random(1);
        MultivariateNormalDistribution g1 = new MultivariateNormalDistribution(
            new JDKRandomGenerator(random.nextInt()),
            new double[] { 0.0, 0.0 },
            new double[][] { { 2.0, 1.0 }, { 1.0, 2.0 } }
        );
        MultivariateNormalDistribution g2 = new MultivariateNormalDistribution(
            new JDKRandomGenerator(random.nextInt()),
            new double[] { 10.0, 10.0 },
            new double[][] { { 2.0, 1.0 }, { 1.0, 2.0 } }
        );
        MultivariateNormalDistribution[] normalDistributions = new MultivariateNormalDistribution[] { g1, g2 };
        long startTime = 1648154137000l;
        for (int i = 0; i < size; ++i) {
            int id = 0;
            if (Math.random() < 0.5) {
                id = 1;
            }
            double[] sample = normalDistributions[id].sample();
            Object[] row = Arrays.stream(sample).boxed().toArray(Double[]::new);
            if (addTimeFiled) {
                long timestamp = startTime + 60_000 * i;
                row = new Object[] { row[0], row[1], timestamp };
            }
            dataFrame.appendRow(row);
        }

        return dataFrame;
    }

    public static void endecryptConnectorCredentials(Connector connector, Encryptor encryptor, boolean encrypt) {
        CountDownLatch latch = new CountDownLatch(1);
        ActionListener<Boolean> listener = ActionListener.wrap(r -> { latch.countDown(); }, e -> { latch.countDown(); });
        if (encrypt) {
            connector.encrypt(encryptor::encrypt, null, listener);
        } else {
            connector
                .decrypt(
                    Optional
                        .ofNullable(connector.getActions())
                        .map(List::getFirst)
                        .map(ConnectorAction::getActionType)
                        .map(Enum::name)
                        .orElse(null),
                    encryptor::decrypt,
                    null,
                    listener
                );
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            fail("Failed to encrypt credentials in connector, " + e.getMessage());
        }
    }

    public static String encryptCredentials(List<String> plainTexts, String tenantId, Encryptor encryptor) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<String>> encryptedResults = new AtomicReference<>();
        AtomicReference<RuntimeException> exceptionAtomicReference = new AtomicReference<>();
        ActionListener<List<String>> listener = ActionListener.wrap(r -> {
            latch.countDown();
            encryptedResults.set(r);
        }, e -> {
            latch.countDown();
            exceptionAtomicReference.set((RuntimeException) e);
        });
        encryptor.encrypt(plainTexts, tenantId, listener);
        try {
            latch.await();
        } catch (InterruptedException e) {
            fail("Failed to encrypt credentials in connector, " + e.getMessage());
        }
        if (exceptionAtomicReference.get() != null) {
            throw exceptionAtomicReference.get();
        }
        return encryptedResults.get().getFirst();
    }

    public static String decryptCredentials(List<String> encryptedTexts, String tenantId, Encryptor encryptor) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<String>> decryptedResults = new AtomicReference<>();
        AtomicReference<RuntimeException> exceptionAtomicReference = new AtomicReference<>();
        ActionListener<List<String>> listener = ActionListener.wrap(r -> {
            latch.countDown();
            decryptedResults.set(r);
        }, e -> {
            latch.countDown();
            exceptionAtomicReference.set((RuntimeException) e);
        });
        encryptor.decrypt(encryptedTexts, tenantId, listener);
        try {
            latch.await();
        } catch (InterruptedException e) {
            fail("Failed to decrypt credentials in connector, " + e.getMessage());
        }
        if (exceptionAtomicReference.get() != null) {
            throw exceptionAtomicReference.get();
        }
        return decryptedResults.get().getFirst();
    }

}
