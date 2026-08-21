package io.github.kenvandine.solstice.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Messages {

    private final String prefix;
    private final Map<String, String> flat;

    private Messages(String prefix, Map<String, String> flat) {
        this.prefix = prefix;
        this.flat = flat;
    }

    public static Messages load(FileConfiguration yaml) {
        Map<String, String> flat = new LinkedHashMap<>();
        flatten(yaml.getValues(true), "", flat);
        String prefix = ChatColor.translateAlternateColorCodes('&', flat.getOrDefault("prefix", ""));
        return new Messages(prefix, flat);
    }

    private static void flatten(Map<?, ?> section, String path, Map<String, String> out) {
        for (Map.Entry<?, ?> entry : section.entrySet()) {
            String key = path.isEmpty() ? String.valueOf(entry.getKey()) : path + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof org.bukkit.configuration.ConfigurationSection cs) {
                flatten(cs.getValues(false), key, out);
            } else if (value instanceof Map<?, ?> map) {
                flatten(map, key, out);
            } else if (value != null) {
                out.put(key, String.valueOf(value));
            }
        }
    }

    /** Raw message with '&' color codes translated, no prefix, no placeholder substitution. */
    public String raw(String key) {
        return ChatColor.translateAlternateColorCodes('&', flat.getOrDefault(key, key));
    }

    /** Message with the configured prefix prepended. */
    public String get(String key) {
        return prefix + raw(key);
    }

    public String get(String key, Map<String, String> placeholders) {
        return apply(get(key), placeholders);
    }

    public String rawFormat(String key, Map<String, String> placeholders) {
        return apply(raw(key), placeholders);
    }

    private String apply(String text, Map<String, String> placeholders) {
        String result = text;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            result = result.replace("%" + e.getKey() + "%", e.getValue());
        }
        return result;
    }
}
