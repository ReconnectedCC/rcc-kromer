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
        }

        public ParseResult(Map<String, String> pairs, boolean success, String error) {
            this.pairs = pairs;
            this.success = success;
            this.error = error;
        }
    }

    // Hand-written parser matching your grammar
    private static ParseResult parseInternal(String input) {
        ParseResult result = new ParseResult();
        result.success = true;

        int i = 0;
        int len = input.length();

        // Helper to skip whitespace
        Runnable skipWS;

        // Wrap i in array so lambdas can modify it
        final int[] iArr = { 0 };
        skipWS = () -> {
            while (iArr[0] < len && (input.charAt(iArr[0]) == ' ' || input.charAt(iArr[0]) == '\t')) {
                iArr[0]++;
            }
        };

        skipWS.run();

        try {
            while (iArr[0] < len) {
                // semicolons can appear anywhere
                if (input.charAt(iArr[0]) == ';') {
                    iArr[0]++;
                    skipWS.run();
                    continue;
                }

                // parse key
                int startKey = iArr[0];
                while (iArr[0] < len) {
                    char c = input.charAt(iArr[0]);
                    if (Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '-' || c == '@') {
                        iArr[0]++;
                    } else {
                        break;
                    }
                }
                if (startKey == iArr[0]) {
                    throw new RuntimeException("Expected key at position " + iArr[0]);
                }
                String key = input.substring(startKey, iArr[0]);

                skipWS.run();

                String value = "";
                if (iArr[0] < len && input.charAt(iArr[0]) == '=') {
                    iArr[0]++; // skip '='
                    skipWS.run();

                    int startValue = iArr[0];
                    while (iArr[0] < len) {
                        char c = input.charAt(iArr[0]);
                        if (c == ';' || c == '\n' || c == '\r') break;
                        iArr[0]++;
                    }
                    value = input.substring(startValue, iArr[0]).trim();
                }

                result.pairs.put(key, value);

                skipWS.run();

                // optional semicolon before next pair
                if (iArr[0] < len && input.charAt(iArr[0]) == ';') {
                    iArr[0]++;
                    skipWS.run();
                }
            }
        } catch (RuntimeException e) {
            result.success = false;
            result.error = e.getMessage();
        }

        return result;
    }

    // API methods
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

    // For quick manual testing
    public static void main(String[] args) {
        String test = "name=John; email=john@example.com; flag";
        System.out.println(toJson(test));
    }
}
