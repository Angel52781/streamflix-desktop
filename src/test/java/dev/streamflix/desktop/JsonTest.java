package dev.streamflix.desktop;

import java.util.*;

public final class JsonTest {
    public static void main(String[] args) {
        String source = "{\"a\":1,\"b\":[true,null,\"x\\n\\u00f1\"],\"c\":-2.5e2}";
        Map<String,Object> obj = Json.object(Json.parse(source));
        require(Json.integer(obj.get("a")) == 1, "integer");
        List<Object> b = Json.array(obj.get("b"));
        require(Boolean.TRUE.equals(b.get(0)), "boolean");
        require(b.get(1) == null, "null");
        require("x\nñ".equals(b.get(2)), "escape/unicode");
        require(Math.abs(Json.decimal(obj.get("c")) + 250.0) < 0.001, "decimal");

        String roundtrip = Json.stringify(Map.of("x", List.of(1, 2, "t"), "ok", true));
        Map<String,Object> rt = Json.object(Json.parse(roundtrip));
        require(Boolean.TRUE.equals(rt.get("ok")), "roundtrip");
        System.out.println("JsonTest OK");
    }

    private static void require(boolean value, String name) {
        if (!value) throw new AssertionError("Failed: " + name);
    }
}
