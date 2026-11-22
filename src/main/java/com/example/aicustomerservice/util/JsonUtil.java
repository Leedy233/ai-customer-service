package com.example.aicustomerservice.util;

import java.util.List;
import java.util.Map;

/**
 * 加法：零依赖 JSON 序列化工具（仅覆盖本项目落库所需的固定形状：
 * 字符串键值对象、溯源数组）。避免直接绑定 Jackson 大版本包名。
 */
public final class JsonUtil {

    private JsonUtil() {
    }

    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    /** {"k":"v",...}（保持插入序） */
    public static String stringMap(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":\"").append(escape(e.getValue())).append('"');
        }
        return sb.append('}').toString();
    }

    public record SourceRef(String doc, String section, String excerpt) {
    }

    /** [{"doc":..,"section":..,"excerpt":..},...] */
    public static String sources(List<SourceRef> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            SourceRef r = list.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"doc\":\"").append(escape(r.doc()))
                    .append("\",\"section\":\"").append(escape(r.section()))
                    .append("\",\"excerpt\":\"").append(escape(r.excerpt())).append("\"}");
        }
        return sb.append(']').toString();
    }
}