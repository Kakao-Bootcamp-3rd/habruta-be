package com.imyme.mine.domain.ai.dto.solo;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 서버의 keyword 응답이 문자열 배열 또는 중첩 배열인 경우 모두 수용
 * 예) ["a","b"] 또는 [["a"],["b"]]
 */
public class KeywordListDeserializer extends JsonDeserializer<List<String>> {

    @Override
    public List<String> deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        return flatten(node);
    }

    private List<String> flatten(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node == null || node.isNull()) {
            return result;
        }
        if (node.isTextual()) {
            result.add(node.asText());
            return result;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (child == null || child.isNull()) {
                    continue;
                }
                if (child.isArray()) {
                    result.addAll(flatten(child));
                } else if (child.isTextual()) {
                    result.add(child.asText());
                } else {
                    result.add(child.asText());
                }
            }
            return result;
        }
        result.add(node.asText());
        return result;
    }
}