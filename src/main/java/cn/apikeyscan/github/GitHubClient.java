package cn.apikeyscan.github;

import cn.apikeyscan.util.Json;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GitHubClient {
    private static final String API_VERSION = "2026-03-10";
    private final HttpClient http;
    private final String token;
    private final long requestDelayMs;
    private Instant lastSearchRequest = Instant.EPOCH;

    public GitHubClient(String token, long requestDelayMs) {
        this.token = token;
        this.requestDelayMs = requestDelayMs;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public SearchPage searchCode(String query, int page, int perPage) throws IOException, InterruptedException {
        throttleSearch();
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");
        URI uri = URI.create("https://api.github.com/search/code?q=" + encoded + "&page=" + page + "&per_page=" + perPage);
        HttpRequest request = baseRequest(uri)
                .header("Accept", "application/vnd.github+json")
                .GET().build();
        HttpResponse<String> response = sendWithRetry(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8), true);
        Object raw = Json.parse(response.body());
        if (!(raw instanceof Map<?, ?> root)) throw new IOException("GitHub 搜索响应不是 JSON 对象");
        long total = number(root.get("total_count"));
        boolean incomplete = Boolean.TRUE.equals(root.get("incomplete_results"));
        List<SearchItem> items = new ArrayList<>();
        Object rawItems = root.get("items");
        if (rawItems instanceof Iterable<?> list) {
            for (Object itemRaw : list) {
                if (!(itemRaw instanceof Map<?, ?> item)) continue;
                Map<?, ?> repo = item.get("repository") instanceof Map<?, ?> m ? m : Map.of();
                items.add(new SearchItem(
                        number(repo.get("id")),
                        string(repo.get("full_name")),
                        Boolean.TRUE.equals(repo.get("fork")),
                        string(item.get("path")),
                        string(item.get("sha")),
                        string(item.get("url")),
                        string(item.get("html_url"))
                ));
            }
        }
        return new SearchPage(total, incomplete, List.copyOf(items));
    }

    public String downloadContent(SearchItem item) throws IOException, InterruptedException {
        HttpRequest request = baseRequest(URI.create(item.apiUrl()))
                .header("Accept", "application/vnd.github.raw+json")
                .GET().build();
        HttpResponse<byte[]> response = sendWithRetry(request, HttpResponse.BodyHandlers.ofByteArray(), false);
        byte[] bytes = response.body();
        if (bytes.length > 2_000_000) throw new IOException("文件超过 2MB，跳过");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private HttpRequest.Builder baseRequest(URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + token)
                .header("X-GitHub-Api-Version", API_VERSION)
                .header("User-Agent", "ApiKey_Scan/1.0");
        return builder;
    }

    private <T> HttpResponse<T> sendWithRetry(HttpRequest request, HttpResponse.BodyHandler<T> handler, boolean search)
            throws IOException, InterruptedException {
        for (int attempt = 1; attempt <= 4; attempt++) {
            HttpResponse<T> response = http.send(request, handler);
            int status = response.statusCode();
            if (status >= 200 && status < 300) return response;

            String remaining = response.headers().firstValue("x-ratelimit-remaining").orElse("");
            String reset = response.headers().firstValue("x-ratelimit-reset").orElse("");
            if ((status == 403 || status == 429) && attempt < 4) {
                long waitMs = computeWaitMs(remaining, reset, attempt, search);
                System.err.printf(Locale.ROOT, "GitHub 限流/拒绝 (HTTP %d)，等待 %.1f 秒后重试%n", status, waitMs / 1000.0);
                Thread.sleep(waitMs);
                continue;
            }
            String body = response.body() instanceof byte[] bytes
                    ? new String(bytes, StandardCharsets.UTF_8)
                    : String.valueOf(response.body());
            if (body.length() > 800) body = body.substring(0, 800);
            throw new IOException("GitHub API HTTP " + status + ": " + body);
        }
        throw new IOException("GitHub API 请求失败");
    }

    private long computeWaitMs(String remaining, String reset, int attempt, boolean search) {
        if ("0".equals(remaining) && !reset.isBlank()) {
            try {
                long resetEpoch = Long.parseLong(reset);
                return Math.max(1_000, resetEpoch * 1000L - System.currentTimeMillis() + 2_000);
            } catch (NumberFormatException ignored) {}
        }
        long base = search ? Math.max(10_000, requestDelayMs) : 2_000;
        return Math.min(120_000, base * attempt);
    }

    private synchronized void throttleSearch() throws InterruptedException {
        long elapsed = Duration.between(lastSearchRequest, Instant.now()).toMillis();
        long remaining = requestDelayMs - elapsed;
        if (remaining > 0) Thread.sleep(remaining);
        lastSearchRequest = Instant.now();
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static long number(Object value) {
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(value)); } catch (Exception ignored) { return 0; }
    }

    public record SearchPage(long totalCount, boolean incomplete, List<SearchItem> items) {}
}


