package com.school.student_manage.ai.tool;

import java.util.LinkedHashMap;
import java.util.Map;

/** JSON Schema 构造小工具，让各工具的参数声明保持简洁可读 */
public final class Schemas {

    private Schemas() {
    }

    public static Map<String, Object> object(Map<String, Object> properties, String... required) {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (required != null && required.length > 0) {
            java.util.List<String> requiredList = new java.util.ArrayList<String>();
            for (String item : required) {
                requiredList.add(item);
            }
            schema.put("required", requiredList);
        }
        return schema;
    }

    public static Map<String, Object> string(String description) {
        return typed("string", description);
    }

    public static Map<String, Object> enumString(String description, String... values) {
        Map<String, Object> schema = typed("string", description);
        java.util.List<String> list = new java.util.ArrayList<String>();
        for (String value : values) {
            list.add(value);
        }
        schema.put("enum", list);
        return schema;
    }

    public static Map<String, Object> integer(String description) {
        return typed("integer", description);
    }

    public static Map<String, Object> number(String description) {
        return typed("number", description);
    }

    private static Map<String, Object> typed(String type, String description) {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", type);
        schema.put("description", description);
        return schema;
    }
}
