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

package org.opensearch.ml.common.transport.search;

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
public class SearchTaskRequest extends ActionRequest {

    /**
     * version id, in case there is future schema change. This can be used to detect which version the client is using.
     */
    int version;
    /**
     * model id
     */
    String modelId;
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

    @Builder
    public SearchTaskRequest(String modelId, String name, String format, String algorithm) {
        this.version = 1;
        this.modelId = modelId;
        this.name = name;
        this.format = format;
        this.algorithm = algorithm;
    }

    public SearchTaskRequest(StreamInput in) throws IOException {
        super(in);
        this.version = in.readInt();
        this.modelId = in.readString();
        this.name = in.readString();
        this.format = in.readString();
        this.algorithm = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeInt(this.version);
        out.writeString(this.modelId);
        out.writeString(this.name);
        out.writeString(this.format);
        out.writeString(this.algorithm);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        return exception;
    }

    public static SearchTaskRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof SearchTaskRequest) {
            return (SearchTaskRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new SearchTaskRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into SearchTaskRequest", e);
        }

    }
}
