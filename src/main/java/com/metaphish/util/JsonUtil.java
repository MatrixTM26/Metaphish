package com.metaphish.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonUtil {

    private JsonUtil() {}

    public static String ToJson(Map<String, ?> Data) {
        StringBuilder Builder = new StringBuilder("{");
        boolean First = true;
        for (Map.Entry<String, ?> Entry : Data.entrySet()) {
            if (!First) Builder.append(",");
            First = false;
            Builder.append("\"").append(EscapeString(Entry.getKey())).append("\":");
            Object Value = Entry.getValue();
            if (Value == null) {
                Builder.append("null");
            } else if (Value instanceof Number || Value instanceof Boolean) {
                Builder.append(Value);
            } else if (Value instanceof Map<?, ?> MapValue) {
                @SuppressWarnings("unchecked")
                Map<String, ?> TypedMapValue = (Map<String, ?>) MapValue;
                Builder.append(ToJson(TypedMapValue));
            } else if (Value instanceof List<?> ListValue) {
                Builder.append(SerializeList(ListValue));
            } else {
                Builder.append("\"").append(EscapeString(Value.toString())).append("\"");
            }
        }
        Builder.append("}");
        return Builder.toString();
    }

    private static String SerializeList(List<?> Items) {
        StringBuilder Builder = new StringBuilder("[");
        boolean First = true;
        for (Object Item : Items) {
            if (!First) Builder.append(",");
            First = false;
            if (Item instanceof Map<?, ?> MapItem) {
                @SuppressWarnings("unchecked")
                Map<String, ?> TypedMap = (Map<String, ?>) MapItem;
                Builder.append(ToJson(TypedMap));
            } else if (Item == null) {
                Builder.append("null");
            } else {
                Builder.append("\"").append(EscapeString(Item.toString())).append("\"");
            }
        }
        Builder.append("]");
        return Builder.toString();
    }

    public static String ToJsonArray(List<? extends Map<String, ?>> List) {
        StringBuilder Builder = new StringBuilder("[");
        boolean First = true;
        for (Map<String, ?> Item : List) {
            if (!First) Builder.append(",");
            First = false;
            Builder.append(ToJson(Item));
        }
        Builder.append("]");
        return Builder.toString();
    }

    public static LinkedHashMap<String, String> ParseObject(String Json) {
        if (Json == null || Json.isBlank()) return new LinkedHashMap<>();
        JsonReader Reader = new JsonReader(Json.trim());
        return Reader.ReadObject();
    }

    public static List<Map<String, String>> ParseArray(String Json) {
        if (Json == null || Json.isBlank()) return new ArrayList<>();
        JsonReader Reader = new JsonReader(Json.trim());
        return Reader.ReadArray();
    }

    private static String EscapeString(String Text) {
        return Text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static final class JsonReader {
        private final String Source;
        private int Position;

        JsonReader(String Source) {
            this.Source = Source;
            this.Position = 0;
        }

        LinkedHashMap<String, String> ReadObject() {
            LinkedHashMap<String, String> Result = new LinkedHashMap<>();
            SkipWhitespace();
            if (Position >= Source.length() || Source.charAt(Position) != '{') return Result;
            Position++;
            SkipWhitespace();
            while (Position < Source.length() && Source.charAt(Position) != '}') {
                SkipWhitespace();
                if (Source.charAt(Position) != '"') break;
                String Key = ReadString();
                SkipWhitespace();
                if (Position < Source.length() && Source.charAt(Position) == ':') Position++;
                SkipWhitespace();
                String Value = ReadValue();
                Result.put(Key, Value);
                SkipWhitespace();
                if (Position < Source.length() && Source.charAt(Position) == ',') Position++;
                SkipWhitespace();
            }
            if (Position < Source.length()) Position++;
            return Result;
        }

        List<Map<String, String>> ReadArray() {
            List<Map<String, String>> Result = new ArrayList<>();
            SkipWhitespace();
            if (Position >= Source.length() || Source.charAt(Position) != '[') return Result;
            Position++;
            SkipWhitespace();
            while (Position < Source.length() && Source.charAt(Position) != ']') {
                SkipWhitespace();
                if (Source.charAt(Position) == '{') {
                    Result.add(ReadObject());
                } else {
                    SkipValue();
                }
                SkipWhitespace();
                if (Position < Source.length() && Source.charAt(Position) == ',') Position++;
                SkipWhitespace();
            }
            if (Position < Source.length()) Position++;
            return Result;
        }

        private String ReadValue() {
            if (Position >= Source.length()) return "";
            char Current = Source.charAt(Position);
            if (Current == '"') return ReadString();
            if (Current == '{') {
                int Start = Position;
                SkipObject();
                return Source.substring(Start, Position);
            }
            if (Current == '[') {
                int Start = Position;
                SkipArray();
                return Source.substring(Start, Position);
            }
            int Start = Position;
            while (Position < Source.length() && Source.charAt(Position) != ',' && Source.charAt(Position) != '}' && Source.charAt(Position) != ']') {
                Position++;
            }
            return Source.substring(Start, Position).trim();
        }

        private String ReadString() {
            if (Source.charAt(Position) != '"') return "";
            Position++;
            StringBuilder Builder = new StringBuilder();
            while (Position < Source.length()) {
                char Ch = Source.charAt(Position++);
                if (Ch == '"') break;
                if (Ch == '\\' && Position < Source.length()) {
                    char Escape = Source.charAt(Position++);
                    switch (Escape) {
                        case '"' -> Builder.append('"');
                        case '\\' -> Builder.append('\\');
                        case '/' -> Builder.append('/');
                        case 'n' -> Builder.append('\n');
                        case 'r' -> Builder.append('\r');
                        case 't' -> Builder.append('\t');
                        case 'b' -> Builder.append('\b');
                        case 'f' -> Builder.append('\f');
                        case 'u' -> {
                            if (Position + 4 <= Source.length()) {
                                String Hex = Source.substring(Position, Position + 4);
                                try {
                                    Builder.append((char) Integer.parseInt(Hex, 16));
                                    Position += 4;
                                } catch (NumberFormatException Ignored) {
                                    Builder.append(Escape);
                                }
                            }
                        }
                        default -> Builder.append(Escape);
                    }
                } else {
                    Builder.append(Ch);
                }
            }
            return Builder.toString();
        }

        private void SkipWhitespace() {
            while (Position < Source.length() && Character.isWhitespace(Source.charAt(Position))) Position++;
        }

        private void SkipObject() {
            if (Position >= Source.length() || Source.charAt(Position) != '{') return;
            int Depth = 0;
            while (Position < Source.length()) {
                char Ch = Source.charAt(Position++);
                if (Ch == '{') Depth++;
                else if (Ch == '}') { Depth--; if (Depth == 0) return; }
                else if (Ch == '"') SkipStringContent();
            }
        }

        private void SkipArray() {
            if (Position >= Source.length() || Source.charAt(Position) != '[') return;
            int Depth = 0;
            while (Position < Source.length()) {
                char Ch = Source.charAt(Position++);
                if (Ch == '[') Depth++;
                else if (Ch == ']') { Depth--; if (Depth == 0) return; }
                else if (Ch == '"') SkipStringContent();
            }
        }

        private void SkipValue() {
            if (Position >= Source.length()) return;
            char Ch = Source.charAt(Position);
            if (Ch == '"') { Position++; SkipStringContent(); }
            else if (Ch == '{') SkipObject();
            else if (Ch == '[') SkipArray();
            else { while (Position < Source.length() && Source.charAt(Position) != ',' && Source.charAt(Position) != '}' && Source.charAt(Position) != ']') Position++; }
        }

        private void SkipStringContent() {
            while (Position < Source.length()) {
                char Ch = Source.charAt(Position++);
                if (Ch == '"') return;
                if (Ch == '\\') Position++;
            }
        }
    }
}
