package cc.reconnected.kromer.common;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CommonMeta {

    public final HashMap<String, String> keywordEntries;
    public final List<String> positionalEntries;

    public CommonMeta() {
        this.keywordEntries = new HashMap<>();
        this.positionalEntries = new ArrayList<>();
    }

    public CommonMeta(@NotNull HashMap<String, String> keywordEntries, @NotNull List<String> positionalEntries) {
        this.keywordEntries = keywordEntries;
        this.positionalEntries = positionalEntries;
    }

    public static CommonMeta fromString(@NotNull String input) {
        HashMap<String, String> keywordEntries = new HashMap<>();
        List<String> positionalEntries = new ArrayList<>();

        for (String entry : input.split(";")) {
            if (entry.isEmpty()) continue;

            if (entry.contains("=")) { // keyword entry
                String[] parts = entry.split("=", 2);

                // im not sure if this is the correct behavior (it's probably fine)
                if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                    keywordEntries.put(parts[0], parts[1]);
                }
            } else { // positional entry
                positionalEntries.add(entry);
            }
        }

        return new CommonMeta(keywordEntries, positionalEntries);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        for (String positional : positionalEntries) {
            if (!sb.isEmpty()) sb.append(";");
            sb.append(positional);
        }

        for (var entry : keywordEntries.entrySet()) {
            if (!sb.isEmpty()) sb.append(";");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }

        return sb.toString();
    }

    public CommonMeta addFromOther(CommonMeta other) { // other takes precedence
        this.keywordEntries.putAll(other.keywordEntries);
        this.positionalEntries.addAll(other.positionalEntries);
        return this;
    }
}
