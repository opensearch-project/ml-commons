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
import java.util.Arrays;
import java.util.Base64;

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

import org.pmml4s.model.Model;

import static org.opensearch.action.ValidateActions.addValidationError;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class UploadTaskRequest extends ActionRequest {
    static final String[] supportedFormats = new String[]{"pmml"};

    /**
     * version id, in case there is future schema change. This can be used to detect which version the client is using.
     */
    int version;
    /**
     * model name
     */
    String name;
    /**
     * model format
     */
    String format;
    /**
     * model algorithm
     */
    String algorithm;
    /**
     * model body
     */
    String body;

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
            exception = addValidationError("model name can't be null or empty", exception);
        }
        if (Strings.isNullOrEmpty(this.format)) {
            exception = addValidationError("model format can't be null or empty", exception);
        } else {
            if (!Arrays.asList(supportedFormats).contains(format.toLowerCase())) {
                exception = addValidationError("only pmml models are supported in upload now", exception);
            }
        }
        if (Strings.isNullOrEmpty(this.algorithm)) {
            exception = addValidationError("algorithm name can't be null or empty", exception);
        }
        if (Strings.isNullOrEmpty(this.body)) {
            exception = addValidationError("model body can't be null or empty", exception);
        }

        // make sure model body (base64 encoded string) can be decoded and turned into a valid model
        try {
            byte[] bodyBytes = Base64.getDecoder().decode(body);
            Model model = Model.fromBytes(bodyBytes);
            return exception;
        } catch (Exception e) {
            exception = addValidationError("can't retrieve model from body passed in", exception);
            return exception;
        }
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
