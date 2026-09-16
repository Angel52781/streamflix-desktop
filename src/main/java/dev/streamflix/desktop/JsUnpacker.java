package dev.streamflix.desktop;

import java.util.*;
import java.util.regex.*;

final class JsUnpacker {
    private final String packed;
    JsUnpacker(String packed) { this.packed = packed == null ? "" : packed; }

    boolean detect() {
        return packed.replace(" ", "").contains("eval(function(p,a,c,k,e,");
    }

    String unpack() {
        try {
            int eval = packed.indexOf("eval(function");
            if (eval < 0) return null;
            int invoke = packed.indexOf("}('", eval);
            if (invoke < 0) return null;
            ParsedString payload = parseSingleQuoted(packed, invoke + 2);
            int pos = skipWsComma(packed, payload.next());
            ParsedInt radix = parseInt(packed, pos);
            pos = skipWsComma(packed, radix.next());
            ParsedInt count = parseInt(packed, pos);
            pos = skipWsComma(packed, count.next());
            ParsedString table = parseSingleQuoted(packed, pos);
            String[] symtab = table.value().split("\\|", -1);
            if (symtab.length != count.value()) return null;
            return decode(payload.value(), radix.value(), symtab);
        } catch (Exception ex) {
            return null;
        }
    }
    private static String decode(String payload, int radix, String[] symtab) {
        Unbase unbase = new Unbase(radix);
        Matcher words = Pattern.compile("\\b\\w+\\b").matcher(payload);
        StringBuilder decoded = new StringBuilder(payload);
        int offset = 0;
        while (words.find()) {
            int index;
            try { index = unbase.unbase(words.group()); }
            catch (Exception ex) { continue; }
            if (index < 0 || index >= symtab.length) continue;
            String value = symtab[index];
            if (value == null || value.isEmpty()) continue;
            decoded.replace(words.start() + offset, words.end() + offset, value);
            offset += value.length() - words.group().length();
        }
        return decoded.toString();
    }

    private static ParsedString parseSingleQuoted(String s, int quotePos) {
        if (quotePos >= s.length() || s.charAt(quotePos) != '\'') throw new IllegalArgumentException("quote expected");
        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        for (int i = quotePos + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped) {
                if (c == 'n') out.append('\n');
                else if (c == 'r') out.append('\r');
                else if (c == 't') out.append('\t');
                else out.append(c);
                escaped = false;
            } else if (c == '\\') escaped = true;
            else if (c == '\'') return new ParsedString(out.toString(), i + 1);
            else out.append(c);
        }
        throw new IllegalArgumentException("unterminated string");
    }
    private static int skipWsComma(String s, int pos) {
        while (pos < s.length() && (Character.isWhitespace(s.charAt(pos)) || s.charAt(pos) == ',')) pos++;
        return pos;
    }

    private static ParsedInt parseInt(String s, int pos) {
        int start = pos;
        while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
        if (start == pos) throw new IllegalArgumentException("integer expected");
        return new ParsedInt(Integer.parseInt(s.substring(start, pos)), pos);
    }

    private static final class Unbase {
        private static final String A62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        private static final String A95 = " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~";
        private final int radix;
        private final String alphabet;
        Unbase(int radix) {
            this.radix = radix;
            this.alphabet = radix <= 36 ? null : (radix <= 62 ? A62.substring(0, radix) : A95.substring(0, radix));
        }

        int unbase(String value) {
            if (alphabet == null) return Integer.parseInt(value, radix);
            int result = 0, power = 1;
            for (int i = value.length() - 1; i >= 0; i--) {
                int digit = alphabet.indexOf(value.charAt(i));
                if (digit < 0) throw new IllegalArgumentException("bad digit");
                result += digit * power;
                power *= radix;
            }
            return result;
        }
    }

    private record ParsedString(String value, int next) {}
    private record ParsedInt(int value, int next) {}
}
