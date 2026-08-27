package net.vibmc.network;

/** Minimal JSON helpers for Minecraft text components. */
public final class JsonText {
    private JsonText() {
    }

    public static String component(String text) {
        return "{\"text\":" + quote(text) + "}";
    }

    public static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 2);
        escaped.append('"');
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (character < 0x20 || Character.isSurrogate(character)) {
                        appendUnicodeEscape(escaped, character);
                    } else {
                        escaped.append(character);
                    }
            }
        }
        return escaped.append('"').toString();
    }

    public static String toConsoleText(String component) {
        if (component == null || !component.startsWith("{\"text\":\"") || !component.endsWith("\"}")) {
            return component;
        }
        String value = component.substring(9, component.length() - 2);
        StringBuilder plain = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i + 1 >= value.length()) { plain.append(c); continue; }
            char escaped = value.charAt(++i);
            switch (escaped) {
                case 'n': plain.append('\n'); break; case 'r': plain.append('\r'); break;
                case 't': plain.append('\t'); break; case 'b': plain.append('\b'); break;
                case 'f': plain.append('\f'); break; case '\\': plain.append('\\'); break;
                case '"': plain.append('"'); break;
                default: plain.append(escaped);
            }
        }
        return plain.toString();
    }

    private static void appendUnicodeEscape(StringBuilder target, char character) {
        String hex = Integer.toHexString(character);
        target.append("\\u");
        for (int i = hex.length(); i < 4; i++) {
            target.append('0');
        }
        target.append(hex);
    }
}
