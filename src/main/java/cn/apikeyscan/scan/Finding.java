package cn.apikeyscan.scan;

import java.util.List;
import java.util.Map;

public record Finding(
        String provider,
        String providerSlug,
        String repository,
        long repositoryId,
        String path,
        String blobSha,
        String htmlUrl,
        int line,
        String variable,
        String rawValue,
        String maskedValue,
        String valueFingerprint,
        int valueLength,
        String baseUrl,
        int score,
        String severity,
        List<String> reasons,
        String query,
        String queryCategory
) {
    public Map<String, Object> toMap() {
        return Map.ofEntries(
                Map.entry("provider", provider),
                Map.entry("provider_slug", providerSlug),
                Map.entry("repository", repository),
                Map.entry("repository_id", repositoryId),
                Map.entry("path", path),
                Map.entry("blob_sha", blobSha),
                Map.entry("source_url", htmlUrl),
                Map.entry("line", line),
                Map.entry("variable", variable),
                Map.entry("masked_value", maskedValue),
                Map.entry("value_fingerprint", valueFingerprint),
                Map.entry("value_length", valueLength),
                Map.entry("base_url", baseUrl == null ? "" : baseUrl),
                Map.entry("score", score),
                Map.entry("severity", severity),
                Map.entry("reasons", reasons),
                Map.entry("query", query),
                Map.entry("query_category", queryCategory)
        );
    }
}

