package com.example.foliareward.web;

import java.util.List;
import java.util.Map;

/**
 * 轻量 JSON 构建工具，无第三方依赖。
 */
public final class JsonBuilder {

    private JsonBuilder() {}

    /** 将 Map 序列化为 JSON 对象字符串 */
    public static String object(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escape(entry.getKey())).append("\":");
            sb.append(toJson(entry.getValue()));
        }
        sb.append('}');
        return sb.toString();
    }

    /** 将 List 序列化为 JSON 数组字符串 */
    public static String array(List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Object item : list) {
            if (!first) sb.append(',');
            first = false;
            sb.append(toJson(item));
        }
        sb.append(']');
        return sb.toString();
    }

    /** 将任意值序列化为 JSON */
    @SuppressWarnings("unchecked")
    public static String toJson(Object value) {
        if (value == null)                  return "null";
        if (value instanceof Boolean)       return value.toString();
        if (value instanceof Number)        return value.toString();
        if (value instanceof String)        return '"' + escape((String) value) + '"';
        if (value instanceof Map)           return object((Map<String, Object>) value);
        if (value instanceof List)          return array((List<?>) value);
        return '"' + escape(value.toString()) + '"';
    }

    /** JSON 字符串转义 */
    public static String escape(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length() + 8);
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    /** 快速构建单层 JSON 对象（交替 key-value 参数） */
    public static String simple(Object... kvPairs) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append('"').append(escape(String.valueOf(kvPairs[i]))).append("\":");
            sb.append(toJson(kvPairs[i + 1]));
        }
        sb.append('}');
        return sb.toString();
    }
}
