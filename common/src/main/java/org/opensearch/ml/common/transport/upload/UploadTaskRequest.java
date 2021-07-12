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

package org.opensearch.ml.common.transport.upload;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.Strings;
import org.opensearch.common.io.stream.InputStreamStreamInput;
import org.opensearch.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import static org.opensearch.action.ValidateActions.addValidationError;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class UploadTaskRequest extends ActionRequest {

    /**
     * name of the model
     */
    String name;

    /**
     * format of the model
     */
    String format;

    /**
     * name of the algorithm
     */
    String algorithm;

    /**
     * body of the model
     */
    String body;

    /**
     * version id, in case there is future schema change. This can be used to detect which version the client is using.
     */
    int version;

    @Builder
    public UploadTaskRequest(String name, String format, String algorithm, String body) {
        this.name = name;
        this.format = format;
        this.algorithm = algorithm;
        this.body = body;
        this.version = 1;
    }

    public UploadTaskRequest(StreamInput in) throws IOException {
        super(in);
        this.version = in.readInt();
        this.name = in.readString();
        this.format = in.readString();
        this.algorithm = in.readString();
        this.body = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeInt(this.version);
        out.writeString(this.name);
        out.writeString(this.format);
        out.writeString(this.algorithm);
        out.writeString(this.body);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (Strings.isNullOrEmpty(this.name)) {
            exception = addValidationError("custom name can't be null or empty", exception);
        }
        if (Strings.isNullOrEmpty(this.format)) {
            exception = addValidationError("model format can't be null or empty", exception);
        }
        if (Strings.isNullOrEmpty(this.algorithm)) {
            exception = addValidationError("algorithm name can't be null or empty", exception);
        }
        if (Strings.isNullOrEmpty(this.body)) {
            exception = addValidationError("model body can't be null or empty", exception);
        }

        return exception;
    }

    public static UploadTaskRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof UploadTaskRequest) {
            return (UploadTaskRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new UploadTaskRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into UploadTaskRequest", e);
        }

    }
}
