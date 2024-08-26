/*
 * Copyright 2023 Aryn
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensearch.searchpipelines.questionanswering.generative.llm;

import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.Setter;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParseException;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.analysis.NameOrDefinition;

import javax.print.Doc;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MessageBlock implements Writeable, ToXContent {

    private static final String TEXT_BLOCK = "text";
    private static final String IMAGE_BLOCK = "image";
    private static final String DOCUMENT_BLOCK = "document";

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(this.role);
        out.writeList(this.blockList);
    }

    public MessageBlock(StreamInput in) throws IOException {
        this.role = in.readString();
        Writeable.Reader<AbstractBlock> reader = input -> {
            String type = input.readString();
            if (type.equals("text")) {
                return new TextBlock(input);
            } else if (type.equals("image")) {
                return new ImageBlock(input);
            } else if (type.equals("document")) {
                return new DocumentBlock(input);
            } else {
                throw new RuntimeException("Unexpected type: " + type);
            }
        };
        this.blockList = in.readList(reader);
    }

    public static MessageBlock fromXContent(XContentParser parser) throws IOException {
        if (parser.currentToken() == XContentParser.Token.START_OBJECT) {
            return new MessageBlock(parser.map());
        }
        throw new XContentParseException(
            parser.getTokenLocation(),
            "Expected [VALUE_STRING] or [START_OBJECT], got " + parser.currentToken()
        );
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field("role", this.role);
        builder.startArray("content");
        for (AbstractBlock block : this.blockList) {
            block.toXContent(builder, params);
        }
        builder.endArray();
        builder.endObject();
        return builder;
    }

    public interface Block {
        String getType();
    }

    public static abstract class AbstractBlock implements Block, Writeable, ToXContent {

        @Override
        abstract public String getType();

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            throw new UnsupportedOperationException("Not implemented.");
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            throw new UnsupportedOperationException("Not implemented.");
        }
    }

    public static class TextBlock extends AbstractBlock {

        @Getter
        String type = "text";

        @Getter
        @Setter
        String text;

        public TextBlock(String text) {
            Preconditions.checkNotNull(text, "text cannot be null.");
            this.text = text;
        }

        public TextBlock(StreamInput in) throws IOException {
            this.text = in.readString();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeString("text");
            out.writeString(this.text);
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {

            builder.startObject();
            builder.field("type", "text");
            builder.field("text", this.text);
            builder.endObject();
            return builder;
        }
    }

    public static class ImageBlock extends AbstractBlock {

        @Getter
        String type = "image";

        @Getter
        @Setter
        String format;

        @Getter
        @Setter
        String data;

        @Getter
        @Setter
        String url;

        public ImageBlock(Map<String, ?> imageBlock) {
            this.format = (String) imageBlock.get("format");
            Object tmp = imageBlock.get("data");
            if (tmp != null) {
                this.data = (String) tmp;
            } else {
                tmp = imageBlock.get("url");
                if (tmp == null) {
                    throw new IllegalArgumentException("data or url not found in imageBlock.");
                }
                this.url = (String) tmp;
            }

        }
        public ImageBlock(String format, String data, String url) {
            Preconditions.checkNotNull(format, "format cannot be null.");
            if (data == null && url == null) {
                throw new IllegalArgumentException("data and url cannot both be null.");
            }
            this.format = format;
            this.data = data;
            this.url = url;
        }

        public ImageBlock(StreamInput in) throws IOException {
            format = in.readString();
            data = in.readOptionalString();
            url = in.readOptionalString();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeString("image");
            out.writeString(this.format);
            out.writeOptionalString(this.data);
            out.writeOptionalString(this.url);
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject();
            Map<String, String> imageMap = new HashMap<>();
            imageMap.put("format", this.format);
            if (this.data != null) {
                imageMap.put("data", this.data);
            } else if (this.url != null) {
                imageMap.put("url", this.url);
            }
            builder.field("image", imageMap);
            builder.endObject();
            return builder;
        }
    }

    static class DocumentBlock extends AbstractBlock {

        @Getter
        String type = "document";

        @Getter
        @Setter
        String format;

        @Getter
        @Setter
        String name;

        @Getter
        @Setter
        String data;

        public DocumentBlock(Map<String, ?> documentBlock) {
            Preconditions.checkState(documentBlock.containsKey("format"), "format not found in the document block.");
            Preconditions.checkState(documentBlock.containsKey("name"), "name not found in the document block.");
            Preconditions.checkState(documentBlock.containsKey("data"), "data not found in the document block");

            this.format = (String) documentBlock.get("format");
            this.name = (String) documentBlock.get("name");
            this.data = (String) documentBlock.get("data");
        }

        public DocumentBlock(String format, String name, String data) {
            Preconditions.checkNotNull(format, "format cannot be null.");
            Preconditions.checkNotNull(name, "name cannot be null.");
            Preconditions.checkNotNull(data, "data cannot be null.");

            this.format = format;
            this.name = name;
            this.data = data;
        }

        public DocumentBlock(StreamInput in) throws IOException {
            format = in.readString();
            name = in.readString();
            data = in.readString();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeString(this.format);
            out.writeString(this.name);
            out.writeString(this.data);
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject();
            builder.startObject("document");
            builder.field("format", this.format);
            builder.field("name", this.name);
            builder.field("data", this.data);
            builder.endObject();
            builder.endObject();
            return builder;
        }
    }

    @Getter
    private String role;

    @Getter
    private List<AbstractBlock> blockList = new ArrayList<>();

    public MessageBlock() {}

    public MessageBlock(Map<String, ?> map) {
        setMessageBlock(map);
    }

    // public <T extends AbstractBlock> T get(int index) {
    //    return (T) this.blockList.get(index);
    // }

    public void setMessageBlock(Map<String, ?> message) {
        Preconditions.checkNotNull(message, "message cannot be null.");
        Preconditions.checkState(message.containsKey("role"),"message must have role." );
        Preconditions.checkState(message.containsKey("content"), "message must have content.");

        this.role = (String) message.get("role");
        List<Map<String, ?>> contents = (List) message.get("content");

        for (Map<String, ?> content : contents) {
            if (content.containsKey(TEXT_BLOCK)) {
                this.blockList.add(new TextBlock((String) content.get(TEXT_BLOCK)));
            } else if (content.containsKey(IMAGE_BLOCK)) {
                Map<String, ?> imageBlock = (Map<String, ?>) content.get(IMAGE_BLOCK);
                this.blockList.add(new ImageBlock(imageBlock));
            } else if (content.containsKey(DOCUMENT_BLOCK)) {
                Map<String, ?> documentBlock = (Map<String, ?>) content.get(DOCUMENT_BLOCK);
                this.blockList.add(new DocumentBlock(documentBlock));
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        // TODO
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.role) + Objects.hashCode(this.blockList);
    }
}




