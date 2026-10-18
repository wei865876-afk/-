package com.school.student_manage.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Map;

/** 轻量 JSON 工具类（避免为 ai 包再引入一层依赖） */
public final class JsonSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            // 日期序列化为 ISO 字符串，避免前端与模型看到时间戳
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JsonSupport() {
    }

    public static String toJson(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new java.util.LinkedHashMap<String, Object>();
        }
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            return new java.util.LinkedHashMap<String, Object>();
        }
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
