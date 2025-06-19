/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.prediction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.Version;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataframe.ColumnType;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.parameter.clustering.KMeansParams;
import org.opensearch.search.builder.SearchSourceBuilder;

import lombok.NonNull;

public class MLPredictionTaskRequestTest {

    private MLInput mlInput;

    @Before
    public void setUp() {
        DataFrame dataFrame = DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {
            {
                put("key1", 2.0D);
            }
        }));
        mlInput = MLInput
            .builder()
            .algorithm(FunctionName.KMEANS)
            .parameters(KMeansParams.builder().centroids(1).build())
            .inputDataset(DataFrameInputDataset.builder().dataFrame(dataFrame).build())
            .build();
    }

    @Test
    public void writeTo_Success() throws IOException {
        User user = User.parse("admin|role-1|all_access");

        MLPredictionTaskRequest request = MLPredictionTaskRequest.builder().mlInput(mlInput).user(user).build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        request.writeTo(bytesStreamOutput);
        request = new MLPredictionTaskRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(FunctionName.KMEANS, request.getMlInput().getAlgorithm());
        KMeansParams params = (KMeansParams) request.getMlInput().getParameters();
        assertEquals(1, params.getCentroids().intValue());
        MLInputDataset inputDataset = request.getMlInput().getInputDataset();
        assertEquals(MLInputDataType.DATA_FRAME, inputDataset.getInputDataType());
        DataFrame dataFrame = ((DataFrameInputDataset) inputDataset).getDataFrame();
        assertEquals(1, dataFrame.size());
        assertEquals(1, dataFrame.columnMetas().length);
        assertEquals("key1", dataFrame.columnMetas()[0].getName());
        assertEquals(ColumnType.DOUBLE, dataFrame.columnMetas()[0].getColumnType());
        assertEquals(1, dataFrame.getRow(0).size());
        assertEquals(2.00, dataFrame.getRow(0).getValue(0).getValue());

        User userExpect = request.getUser();
        assertEquals(user.getName(), userExpect.getName());

        assertNull(request.getModelId());
    }

    @Test
    public void validate_Success() {
        User user = User.parse("admin|role-1|all_access");
        MLPredictionTaskRequest request = MLPredictionTaskRequest.builder().mlInput(mlInput).user(user).build();

        assertNull(request.validate());
    }

    @Test
    public void validate_Exception_NullMLInput() {
        mlInput.setAlgorithm(null);
        MLPredictionTaskRequest request = MLPredictionTaskRequest.builder().build();

        ActionRequestValidationException exception = request.validate();
        assertEquals("Validation Failed: 1: ML input can't be null;", exception.getMessage());
    }

    @Test
    public void validate_Exception_NullInputDataset() {
        mlInput.setInputDataset(null);
        MLPredictionTaskRequest request = MLPredictionTaskRequest.builder().mlInput(mlInput).build();

        ActionRequestValidationException exception = request.validate();

        assertEquals("Validation Failed: 1: input data can't be null;", exception.getMessage());
    }

    @Test
    public void fromActionRequest_Success_WithMLPredictionTaskRequest() {
        MLPredictionTaskRequest request = MLPredictionTaskRequest.builder().mlInput(mlInput).build();
        assertSame(MLPredictionTaskRequest.fromActionRequest(request), request);
    }

    @Test
    public void fromActionRequest_Success_WithNonMLPredictionTaskRequest_DataFrameInput() {
        fromActionRequest_Success_WithNonMLPredictionTaskRequest(mlInput);
    }

    @Test
    public void fromActionRequest_Success_WithNonMLPredictionTaskRequest_SearchQueryInput() {
        @NonNull
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(new MatchAllQueryBuilder());
        mlInput
            .setInputDataset(
                SearchQueryInputDataset
                    .builder()
                    .indices(Collections.singletonList("test_index"))
                    .searchSourceBuilder(searchSourceBuilder)
                    .build()
            );
        fromActionRequest_Success_WithNonMLPredictionTaskRequest(mlInput);
    }

    private void fromActionRequest_Success_WithNonMLPredictionTaskRequest(MLInput mlInput) {
        User user = User.parse("admin|role-1|all_access");
        MLPredictionTaskRequest request = MLPredictionTaskRequest.builder().mlInput(mlInput).user(user).build();
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                request.writeTo(out);
            }
        };
        MLPredictionTaskRequest result = MLPredictionTaskRequest.fromActionRequest(actionRequest);
        assertNotSame(result, request);
        assertEquals(request.getMlInput().getAlgorithm(), result.getMlInput().getAlgorithm());
        assertEquals(request.getMlInput().getInputDataset().getInputDataType(), result.getMlInput().getInputDataset().getInputDataType());
        assertEquals(request.getUser().getName(), request.getUser().getName());
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionRequest_IOException() {
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException("test");
            }
        };
        MLPredictionTaskRequest.fromActionRequest(actionRequest);
    }

    @Test
    public void fromActionRequest_Failure_WithTruncatedStream() {
        MLPredictionTaskRequest request = MLPredictionTaskRequest.builder().mlInput(mlInput).build();

        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                // Write only part of the data to simulate a truncated stream
                out.writeOptionalString(request.getModelId()); // Only writes the modelId
            }
        };

        try {
            MLPredictionTaskRequest.fromActionRequest(actionRequest);
        } catch (UncheckedIOException e) {
            assertEquals("failed to parse ActionRequest into MLPredictionTaskRequest", e.getMessage());
            assertTrue(e.getCause() instanceof EOFException);
        }
    }

    @Test
    public void fromActionRequest_Failure_WithVersionMismatch() {
        MLPredictionTaskRequest request = MLPredictionTaskRequest.builder().mlInput(mlInput).build();

        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                out.setVersion(Version.V_2_18_0); // Simulate an older version
                request.writeTo(out);
            }
        };

        try {
            MLPredictionTaskRequest.fromActionRequest(actionRequest);
        } catch (UncheckedIOException e) {
            assertEquals("failed to parse ActionRequest into MLPredictionTaskRequest", e.getMessage());
        }
    }

    @Test
    public void fromActionRequest_Success_WithNullOptionalFields() throws IOException {
        MLPredictionTaskRequest request = MLPredictionTaskRequest.builder().mlInput(mlInput).tenantId(null).user(null).build();

        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        request.writeTo(bytesStreamOutput);

        MLPredictionTaskRequest result = new MLPredictionTaskRequest(bytesStreamOutput.bytes().streamInput());
        assertNull(result.getUser());
        assertNull(result.getTenantId());
        assertEquals(mlInput.getAlgorithm(), result.getMlInput().getAlgorithm());
    }

    @Test
    public void writeTo_Failure_WithInvalidMLInput() {
        MLInput invalidMLInput = MLInput.builder().algorithm(FunctionName.KMEANS).build();
        MLPredictionTaskRequest request = MLPredictionTaskRequest.builder().mlInput(invalidMLInput).build();

        try (BytesStreamOutput bytesStreamOutput = new BytesStreamOutput()) {
            request.writeTo(bytesStreamOutput);
        } catch (IOException e) {
            assertEquals("ML input can't be null", e.getMessage());
        }
    }

    @Test
    public void integrationTest_FromActionRequest() throws IOException {
        // Create a realistic MLPredictionTaskRequest
        User user = User.parse("test_user|role1|all_access");
        MLPredictionTaskRequest originalRequest = MLPredictionTaskRequest
            .builder()
            .modelId("test_model_id")
            .mlInput(mlInput)
            .user(user)
            .tenantId(null)
            .build();

        // Serialize the request
        BytesStreamOutput out = new BytesStreamOutput();
        originalRequest.writeTo(out);

        // Deserialize it
        MLPredictionTaskRequest deserializedRequest = new MLPredictionTaskRequest(out.bytes().streamInput());

        // Validate the fields
        assertEquals(originalRequest.getModelId(), deserializedRequest.getModelId());
        assertEquals(originalRequest.getMlInput().getAlgorithm(), deserializedRequest.getMlInput().getAlgorithm());
        assertEquals(originalRequest.getUser().getName(), deserializedRequest.getUser().getName());
        assertEquals(originalRequest.getTenantId(), deserializedRequest.getTenantId());
    }

    @Test
    public void integrationTest_FromActionRequest_WithOlderVersion() throws IOException {
        // Simulate an older version that does not support `tenantId`
        Version olderVersion = Version.V_2_18_0; // Replace with an actual older version number
        BytesStreamOutput out = new BytesStreamOutput();
        out.setVersion(olderVersion);

        // Serialize the request with an older version
        MLPredictionTaskRequest originalRequest = MLPredictionTaskRequest
            .builder()
            .modelId("test_model_id")
            .mlInput(mlInput)
            .user(User.parse("test_user|role1|all_access"))
            .tenantId("test_tenant") // This should be ignored in older versions
            .build();
        originalRequest.writeTo(out);

        // Deserialize it
        StreamInput in = out.bytes().streamInput();
        in.setVersion(olderVersion);
        MLPredictionTaskRequest deserializedRequest = new MLPredictionTaskRequest(in);

        // Validate fields
        assertEquals(originalRequest.getModelId(), deserializedRequest.getModelId());
        assertEquals(originalRequest.getMlInput().getAlgorithm(), deserializedRequest.getMlInput().getAlgorithm());
        assertEquals(originalRequest.getUser().getName(), deserializedRequest.getUser().getName());
        assertNull(deserializedRequest.getTenantId()); // tenantId should not exist in older versions
    }

    @Test
    public void integrationTest_FromActionRequest_WithNewerVersion() throws IOException {
        // Simulate a newer version
        Version newerVersion = Version.V_2_19_0; // Replace with the actual newer version number
        BytesStreamOutput out = new BytesStreamOutput();
        out.setVersion(newerVersion);

        // Serialize the request with a newer version
        MLPredictionTaskRequest originalRequest = MLPredictionTaskRequest
            .builder()
            .modelId("test_model_id")
            .mlInput(mlInput)
            .user(User.parse("test_user|role1|all_access"))
            .tenantId("test_tenant")
            .build();
        originalRequest.writeTo(out);

        // Deserialize it
        StreamInput in = out.bytes().streamInput();
        in.setVersion(newerVersion);
        MLPredictionTaskRequest deserializedRequest = new MLPredictionTaskRequest(in);

        // Validate fields
        assertEquals(originalRequest.getModelId(), deserializedRequest.getModelId());
        assertEquals(originalRequest.getMlInput().getAlgorithm(), deserializedRequest.getMlInput().getAlgorithm());
        assertEquals(originalRequest.getUser().getName(), deserializedRequest.getUser().getName());
        assertEquals(originalRequest.getTenantId(), deserializedRequest.getTenantId()); // tenantId should exist
    }

    @Test
    public void integrationTest_FromActionRequest_WithMixedVersion() throws IOException {
        // Serialize with a newer version
        Version newerVersion = Version.V_2_19_0;
        BytesStreamOutput out = new BytesStreamOutput();
        out.setVersion(newerVersion);

        MLPredictionTaskRequest originalRequest = MLPredictionTaskRequest
            .builder()
            .modelId("test_model_id")
            .mlInput(mlInput)
            .user(User.parse("test_user|role1|all_access"))
            .tenantId("test_tenant")
            .build();
        originalRequest.writeTo(out);

        // Deserialize with an older version
        Version olderVersion = Version.V_2_18_0; // Replace with an actual older version number
        StreamInput in = out.bytes().streamInput();
        in.setVersion(olderVersion);

        MLPredictionTaskRequest deserializedRequest = new MLPredictionTaskRequest(in);

        // Validate fields
        assertEquals(originalRequest.getModelId(), deserializedRequest.getModelId());
        assertEquals(originalRequest.getMlInput().getAlgorithm(), deserializedRequest.getMlInput().getAlgorithm());
        assertEquals(originalRequest.getUser().getName(), deserializedRequest.getUser().getName());
        assertNull(deserializedRequest.getTenantId()); // tenantId should not exist in older versions
    }

    @Test
    public void constructor_WithModelIdAndMLInput() {
        // Given
        String modelId = "test_model_id";

        // When
        MLPredictionTaskRequest request = new MLPredictionTaskRequest(modelId, mlInput);

        // Then
        assertEquals(modelId, request.getModelId());
        assertEquals(mlInput, request.getMlInput());
        assertTrue(request.isDispatchTask()); // Default value
        assertNull(request.getUser());       // Default value
        assertNull(request.getTenantId());   // Default value
    }

    @Test
    public void constructor_WithModelIdMLInputUserAndTenantId() {
        // Given
        String modelId = "test_model_id";
        User user = User.parse("admin|role-1|all_access");
        String tenantId = "test_tenant";

        // When
        MLPredictionTaskRequest request = new MLPredictionTaskRequest(modelId, mlInput, user, tenantId);

        // Then
        assertEquals(modelId, request.getModelId());
        assertEquals(mlInput, request.getMlInput());
        assertTrue(request.isDispatchTask()); // Default value
        assertEquals(user, request.getUser());
        assertEquals(tenantId, request.getTenantId());
    }
}
