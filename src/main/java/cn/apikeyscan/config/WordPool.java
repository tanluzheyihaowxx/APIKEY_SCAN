package cn.apikeyscan.config;

import cn.apikeyscan.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record WordPool(
        List<Provider> providers,
        List<String> keyWords,
        List<String> endpoints,
        List<String> configFiles
) {
    public static WordPool load(Path root) throws IOException {
        Path providerDir = root.resolve("providers");
        Path commonDir = root.resolve("common");
        if (!Files.isDirectory(providerDir)) throw new IOException("词池目录不存在: " + providerDir);
        if (!Files.isDirectory(commonDir)) throw new IOException("词池目录不存在: " + commonDir);

        List<Provider> providers = new ArrayList<>();
        try (var stream = Files.list(providerDir)) {
            for (Path path : stream.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                Object raw = Json.read(path);
                if (!(raw instanceof Map<?, ?> map)) continue;
                String name = string(map.get("provider"));
                List<String> envVars = strings(map.get("env_vars"));
                List<String> keywords = strings(map.get("keywords"));
                providers.add(new Provider(slug(path), name, dedupe(envVars), dedupe(keywords)));
            }
        }
        if (providers.isEmpty()) throw new IOException("providers 中未找到有效厂商词池");

        return new WordPool(
                List.copyOf(providers),
                dedupe(readStringArray(commonDir.resolve("key_words.json"))),
                dedupe(readStringArray(commonDir.resolve("endpoint.json"))),
                dedupe(readStringArray(commonDir.resolve("config_files.json")))
        );
    }

    public Set<String> providerSignals(Provider provider) {
        LinkedHashSet<String> signals = new LinkedHashSet<>();
        provider.envVars().forEach(v -> signals.add(v.toLowerCase(Locale.ROOT)));
        provider.keywords().forEach(v -> signals.add(v.toLowerCase(Locale.ROOT)));
        for (String endpoint : endpoints) {
            String lower = endpoint.toLowerCase(Locale.ROOT);
            if (matchesProvider(lower, provider.slug())) signals.add(lower);
        }
        return signals;
    }

    public static boolean matchesProvider(String text, String slug) {
        return switch (slug.toLowerCase(Locale.ROOT)) {
            case "deepseek" -> text.contains("deepseek");
            case "aliyun" -> text.contains("dashscope") || text.contains("aliyun") || text.contains("qwen") || text.contains("bailian") || text.contains("百炼") || text.contains("通义");
            case "moonshot" -> text.contains("moonshot") || text.contains("kimi") || text.contains("月之暗面");
            default -> text.contains(slug.toLowerCase(Locale.ROOT));
        };
    }

    private static String slug(Path path) {
        String name = path.getFileName().toString();
        return name.substring(0, name.length() - ".json".length());
    }

    private static List<String> readStringArray(Path path) throws IOException {
        return strings(Json.read(path));
    }

    private static List<String> strings(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof Iterable<?> items) {
            for (Object item : items) if (item != null && !String.valueOf(item).isBlank()) result.add(String.valueOf(item).trim());
        }
        return result;
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static List<String> dedupe(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values));
    }

    public record Provider(String slug, String name, List<String> envVars, List<String> keywords) {}
}

