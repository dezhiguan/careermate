package com.careermate.common.support;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * 请求体里的 jdDocId 反序列化：{@code "doc-89840"} 与 {@code 89840} 都收。
 *
 * @see JdDocIds
 */
public class JdDocIdDeserializer extends JsonDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        return JdDocIds.parse(p.getValueAsString());
    }
}
