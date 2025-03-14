package org.opensearch.ml.utils;

import java.io.IOException;
import java.time.Instant;

import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.core.xcontent.XContentParserUtils;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class ParseUtils {

    private ParseUtils() {}

    public static Instant toInstant(XContentParser parser) throws IOException {
        if (XContentParser.Token.VALUE_NULL.equals(parser.currentToken())) {
            return null;
        }

        if (parser.currentToken().isValue()) {
            return Instant.ofEpochMilli(parser.longValue());
        }

        XContentParserUtils.throwUnknownToken(parser.currentToken(), parser.getTokenLocation());
        return null;
    }
}
