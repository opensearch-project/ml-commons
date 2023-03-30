/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import lombok.Builder;
import lombok.Getter;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;

@Getter
public class MLModelGroup implements ToXContentObject {
    public static final String MODEL_GROUP_NAME_FIELD = "name";
    // We use int type for version in first release 1.3. In 2.4, we changed to
    // use String type for version. Keep this old version field for old models.
    public static final String DESCRIPTION_FIELD = "description";
    public static final String LATEST_VERSION_FIELD = "latest_version";
    //SHA256 hash value of model content.

    //TODO: add created time, updated time,
    private String name;
    private String description;
    private int latestVersion = 0;


    @Builder(toBuilder = true)
    public MLModelGroup(String name, String description, int latestVersion) {
        this.name = name;
        this.description = description;
        this.latestVersion = latestVersion;
    }


    public MLModelGroup(StreamInput input) throws IOException{
        name = input.readString();
        description = input.readOptionalString();
        latestVersion = input.readInt();
    }

    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeOptionalString(description);
        out.writeInt(latestVersion);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(MODEL_GROUP_NAME_FIELD, name);
        builder.field(LATEST_VERSION_FIELD, latestVersion);
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        builder.endObject();
        return builder;
    }


    public static MLModelGroup fromStream(StreamInput in) throws IOException {
        MLModelGroup mlModel = new MLModelGroup(in);
        return mlModel;
    }
}
