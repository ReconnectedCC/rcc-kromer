package cc.reconnected.kromer;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

public class CommonMetaParser {

    public static class ParseResult {
        public Map<String, String> pairs;
        public boolean success;
        public String error;

        public ParseResult() {
            this.pairs = new HashMap<>();
            this.success = true;
        }

        public ParseResult(Map<String, String> pairs, boolean success, String error) {
            this.pairs = pairs;
            this.success = success;
            this.error = error;
        }
    }

    private static ParseResult parseInternal(String input) {
        ParseResult result = new ParseResult();

        if (input == null || input.isEmpty()) return result;

        int len = input.length();
        int[] iArr = {0};
        int iterations = 0;
        int maxIterations = len * 10;

        Runnable skipWS = () -> {
            while (iArr[0] < len && (input.charAt(iArr[0]) == ' ' || input.charAt(iArr[0]) == '\t' || input.charAt(iArr[0]) == '\n' || input.charAt(iArr[0]) == '\r')) {
                iArr[0]++;
            }
        };

        skipWS.run();

        try {
            while (iArr[0] < len) {
                if (++iterations > maxIterations) {
                    result.success = false;
                    result.error = "Maximum iterations exceeded (possible infinite loop)";
                    break;
                }

                while (iArr[0] < len) {
                    char c = input.charAt(iArr[0]);
                    if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == ';') {
                        iArr[0]++;
                    } else {
                        break;
                    }
                }
                if (iArr[0] >= len) break;

                int startKey = iArr[0];
                while (iArr[0] < len) {
                    char c = input.charAt(iArr[0]);
                    if (c == '=' || c == ';' || c == '\n' || c == '\r') break; // separators stop the key
                    iArr[0]++;
                }
                String key = input.substring(startKey, iArr[0]).trim();

                if (key.isEmpty()) {
                    iArr[0]++;
                    continue;
                }

                String value = "";
                if (iArr[0] < len && input.charAt(iArr[0]) == '=') {
                    iArr[0]++;
                    int startValue = iArr[0];
                    while (iArr[0] < len && input.charAt(iArr[0]) != ';' && input.charAt(iArr[0]) != '\n' && input.charAt(iArr[0]) != '\r') {
                        iArr[0]++;
                    }
                    value = input.substring(startValue, iArr[0]).trim();
                }

                result.pairs.put(key, value);
            }

        } catch (Exception e) {
            result.success = false;
            result.error = e.getMessage();
        }

        return result;
    }

    public static ParseResult parseWithResult(String input) {
        return parseInternal(input);
    }

    public static Map<String, String> parsePairs(String input) {
        ParseResult r = parseInternal(input);
        return r.success ? r.pairs : new HashMap<>();
    }

    public static boolean isValid(String input) {
        return parseInternal(input).success;
    }

    public static String getErrorMessage(String input) {
        return parseInternal(input).error;
    }

    public static String toJson(String input) {
        return new Gson().toJson(parseInternal(input));
    }

    public static void main(String[] args) {
        String test = ";;name=John; email=john@example.com;;flag;;=bad;😀=fun;";
        System.out.println(toJson(test));
    }
}