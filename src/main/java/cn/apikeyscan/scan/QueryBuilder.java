package cn.apikeyscan.scan;

import cn.apikeyscan.config.WordPool;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class QueryBuilder {
    private QueryBuilder() {}

    public static List<QuerySpec> build(WordPool pool, int maxQueries, List<String> extraQueries) {
        LinkedHashMap<String, QuerySpec> queries = new LinkedHashMap<>();
        List<String> genericKeyTerms = select(pool.keyWords(), true);
        List<String> genericBaseTerms = select(pool.keyWords(), false);
        List<String> configFiles = pool.configFiles().stream().limit(7).toList();

        for (WordPool.Provider provider : pool.providers()) {
            for (String env : provider.envVars()) {
                add(queries, new QuerySpec(env, provider.slug(), "provider-env"));
            }
            String primary = provider.keywords().stream()
                    .filter(k -> k.chars().allMatch(c -> c < 128))
                    .findFirst().orElse(provider.slug());

            for (String keyTerm : genericKeyTerms.stream().limit(4).toList()) {
                add(queries, new QuerySpec(primary + " " + keyTerm, provider.slug(), "provider-key-context"));
            }
            for (String baseTerm : genericBaseTerms.stream().limit(4).toList()) {
                add(queries, new QuerySpec(primary + " " + baseTerm, provider.slug(), "provider-baseurl-context"));
            }
            for (String file : configFiles) {
                add(queries, new QuerySpec(primary + " filename:" + file, provider.slug(), "config-file"));
            }

            add(queries, new QuerySpec(primary + " apiKey language:Java", provider.slug(), "java-call"));
            add(queries, new QuerySpec(primary + " api_key language:Python", provider.slug(), "python-call"));
            add(queries, new QuerySpec(primary + " process.env language:JavaScript", provider.slug(), "javascript-call"));
        }

        for (String endpoint : pool.endpoints()) {
            String host = host(endpoint);
            String slug = detectSlug(host);
            add(queries, new QuerySpec(host, slug, "endpoint"));
        }
        for (String query : extraQueries) add(queries, new QuerySpec(query, "", "custom"));

        return queries.values().stream().limit(maxQueries).toList();
    }

    private static List<String> select(List<String> words, boolean key) {
        List<String> result = new ArrayList<>();
        for (String word : words) {
            String normalized = word.toLowerCase(Locale.ROOT).replace("-", "_").replace(".", "_");
            boolean isBase = normalized.contains("base") || normalized.contains("endpoint") || normalized.equals("api_url");
            boolean isKey = normalized.contains("api_key") || normalized.equals("apikey") || normalized.endsWith("api_key_value");
            if ((key && isKey) || (!key && isBase)) result.add(word);
        }
        if (key && result.isEmpty()) result.addAll(List.of("API_KEY", "api_key", "apiKey"));
        if (!key && result.isEmpty()) result.addAll(List.of("BASE_URL", "base_url", "baseUrl"));
        return result;
    }

    private static String host(String endpoint) {
        String value = endpoint.trim();
        try {
            URI uri = URI.create(value.contains("://") ? value : "https://" + value);
            return uri.getHost() == null ? value : uri.getHost();
        } catch (Exception ignored) {
            return value;
        }
    }

    private static String detectSlug(String text) {
        if (WordPool.matchesProvider(text.toLowerCase(Locale.ROOT), "deepseek")) return "deepseek";
        if (WordPool.matchesProvider(text.toLowerCase(Locale.ROOT), "aliyun")) return "aliyun";
        if (WordPool.matchesProvider(text.toLowerCase(Locale.ROOT), "moonshot")) return "moonshot";
        return "";
    }

    private static void add(Map<String, QuerySpec> queries, QuerySpec spec) {
        String q = spec.query().trim().replaceAll("\\s+", " ");
        if (!q.isBlank()) queries.putIfAbsent(q.toLowerCase(Locale.ROOT), new QuerySpec(q, spec.providerSlug(), spec.category()));
    }

    public record QuerySpec(String query, String providerSlug, String category) {}
}

