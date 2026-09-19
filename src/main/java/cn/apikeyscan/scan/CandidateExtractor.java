package cn.apikeyscan.scan;

import cn.apikeyscan.config.WordPool;
import cn.apikeyscan.github.SearchItem;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CandidateExtractor {
    private static final Pattern ASSIGNMENT = Pattern.compile(
            "(?im)^[\\t ]*[\\\"']?(?:(?:public|private|protected|static|final|const|let|var)\\s+)*(?:[A-Za-z_$][A-Za-z0-9_$<>?,\\[\\]]*\\s+)?([A-Za-z_][A-Za-z0-9_.-]{1,120})[\\\"']?[\\t ]*(?:=|:|=>)[\\t ]*[\\\"']?([^\\s\\\"'`,;#]{16,512})[\\\"']?");
    private static final Pattern CALL = Pattern.compile(
            "(?i)\\b([A-Za-z_][A-Za-z0-9_.-]{0,100}(?:api[_-]?key|apikey|token|secret)[A-Za-z0-9_.-]{0,30})\\s*\\(\\s*[\\\"']([^\\\"'\\r\\n]{16,512})[\\\"']");
    private static final Pattern XML = Pattern.compile(
            "(?is)<([A-Za-z_][A-Za-z0-9_.-]*(?:api[_-]?key|apikey|token|secret)[A-Za-z0-9_.-]*)>\\s*([^<\\s]{16,512})\\s*</\\1>");
    private static final Pattern URL = Pattern.compile("(?i)https?://[^\\s\\\"'`),;]+", Pattern.MULTILINE);
    private static final Set<String> PLACEHOLDER_MARKERS = Set.of(
            "your_api", "your-api", "yourkey", "your_key", "replace_me", "replace-me", "changeme",
            "example", "sample", "placeholder", "dummy", "fake", "xxxx", "test_key", "test-key",
            "insert_", "填入", "替换", "这里"
    );

    private final WordPool pool;
    private final Set<String> credentialVariableSignals;

    public CandidateExtractor(WordPool pool) {
        this.pool = pool;
        Set<String> signals = new HashSet<>();
        pool.keyWords().stream()
                .filter(CandidateExtractor::looksLikeCredentialVariable)
                .map(CandidateExtractor::normalize)
                .forEach(signals::add);
        pool.providers().stream().flatMap(p -> p.envVars().stream())
                .map(CandidateExtractor::normalize).forEach(signals::add);
        this.credentialVariableSignals = Set.copyOf(signals);
    }

    public List<Finding> extract(SearchItem item, String content, QueryBuilder.QuerySpec query, int minScore) {
        List<RawCandidate> candidates = new ArrayList<>();
        collect(candidates, ASSIGNMENT, content);
        collect(candidates, CALL, content);
        collect(candidates, XML, content);

        List<LocatedUrl> urls = locateUrls(content);
        Set<String> seen = new HashSet<>();
        List<Finding> findings = new ArrayList<>();
        for (RawCandidate candidate : candidates) {
            String variable = candidate.variable().trim();
            String value = trimValue(candidate.value());
            if (!isCredentialVariable(variable) || !isCandidateValue(value)) continue;
            int line = lineAt(content, candidate.offset());
            String dedup = variable.toLowerCase(Locale.ROOT) + "|" + sha256(value) + "|" + line;
            if (!seen.add(dedup)) continue;

            WordPool.Provider provider = detectProvider(variable, content, candidate.offset(), query.providerSlug());
            LocatedUrl nearest = nearestProviderUrl(urls, line, provider);
            Score score = score(item.path(), variable, value, provider, nearest, content);
            if (score.value() < minScore) continue;

            String sourceUrl = item.htmlUrl();
            if (!sourceUrl.isBlank()) sourceUrl += "#L" + line;
            findings.add(new Finding(
                    provider == null ? "未确定" : provider.name(),
                    provider == null ? "unknown" : provider.slug(),
                    item.repository(), item.repositoryId(), item.path(), item.blobSha(), sourceUrl,
                    line, variable, value, mask(value), sha256(value), value.length(),
                    nearest == null ? "" : nearest.url(), score.value(), severity(score.value()),
                    score.reasons(), query.query(), query.category()
            ));
        }
        findings.sort(Comparator.comparingInt(Finding::score).reversed());
        return findings;
    }

    private static void collect(List<RawCandidate> out, Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) out.add(new RawCandidate(matcher.group(1), matcher.group(2), matcher.start(2)));
    }

    private WordPool.Provider detectProvider(String variable, String content, int offset, String hintSlug) {
        String var = variable.toLowerCase(Locale.ROOT);
        int start = Math.max(0, offset - 1200);
        int end = Math.min(content.length(), offset + 1200);
        String nearby = content.substring(start, end).toLowerCase(Locale.ROOT);
        WordPool.Provider best = null;
        int bestScore = 0;
        for (WordPool.Provider provider : pool.providers()) {
            int score = 0;
            for (String env : provider.envVars()) {
                String e = env.toLowerCase(Locale.ROOT);
                if (normalize(var).contains(normalize(e))) score += 10;
                if (nearby.contains(e)) score += 4;
            }
            for (String keyword : provider.keywords()) {
                String k = keyword.toLowerCase(Locale.ROOT);
                if (var.contains(k)) score += 6;
                if (nearby.contains(k)) score += 3;
            }
            for (String endpoint : pool.endpoints()) {
                String e = endpoint.toLowerCase(Locale.ROOT);
                if (WordPool.matchesProvider(e, provider.slug()) && nearby.contains(hostOnly(e))) score += 6;
            }
            if (provider.slug().equalsIgnoreCase(hintSlug)) score += 1;
            if (score > bestScore) { bestScore = score; best = provider; }
        }
        return bestScore > 0 ? best : null;
    }

    private Score score(String path, String variable, String value, WordPool.Provider provider, LocatedUrl url, String content) {
        int score = 0;
        List<String> reasons = new ArrayList<>();
        String var = normalize(variable);
        String lowerPath = path.toLowerCase(Locale.ROOT);
        String lowerValue = value.toLowerCase(Locale.ROOT);

        if (var.contains("apikey")) { score += 40; reasons.add("变量名包含 API_KEY/APIKEY +40"); }
        else if (var.contains("secret")) { score += 30; reasons.add("变量名包含 SECRET +30"); }
        else if (var.contains("token")) { score += 20; reasons.add("变量名包含 TOKEN +20"); }
        else if (var.contains("key")) { score += 15; reasons.add("变量名包含 KEY +15"); }

        if (isSensitiveConfig(lowerPath)) { score += 20; reasons.add("位于敏感配置文件 +20"); }
        if (value.length() >= 20 && hasCredentialShape(value)) { score += 20; reasons.add("候选值长度和字符结构符合凭据 +20"); }
        if (provider != null && isProviderVariable(variable, provider)) { score += 15; reasons.add("命中厂商专属变量名 +15"); }
        if (url != null) { score += 20; reasons.add("同文件/邻近位置存在目标 API Base URL +20"); }
        if (lowerValue.startsWith("sk-") || lowerValue.startsWith("ak-") || lowerValue.startsWith("key-")) {
            score += 10; reasons.add("值具有常见密钥前缀 +10");
        }

        if (isDocumentationPath(lowerPath)) { score -= 30; reasons.add("README/文档/示例路径 -30"); }
        if (isTestPath(lowerPath)) { score -= 20; reasons.add("测试或 fixture 路径 -20"); }
        if (isPlaceholder(value)) { score -= 45; reasons.add("值疑似占位符/测试数据 -45"); }
        if (isGeneratedOrLockFile(lowerPath)) { score -= 25; reasons.add("生成文件或锁文件 -25"); }

        score = Math.max(0, Math.min(100, score));
        return new Score(score, List.copyOf(reasons));
    }

    private boolean isSensitiveConfig(String path) {
        String filename = path.substring(path.lastIndexOf('/') + 1);
        if (filename.startsWith(".env")) return true;
        for (String configured : pool.configFiles()) {
            String c = configured.toLowerCase(Locale.ROOT);
            if (filename.equals(c) || path.endsWith("/" + c)) return true;
        }
        return path.contains("config/") || path.contains("conf/") || path.contains("deploy/") || path.contains("k8s/");
    }

    private static boolean isDocumentationPath(String path) {
        return path.contains("readme") || path.contains("/docs/") || path.contains("/doc/") ||
                path.contains("example") || path.contains("sample") || path.endsWith(".md") || path.endsWith(".rst");
    }

    private static boolean isTestPath(String path) {
        return path.contains("/test/") || path.contains("/tests/") || path.contains("__tests__") ||
                path.contains("fixture") || path.contains("mock") || path.contains("demo");
    }

    private static boolean isGeneratedOrLockFile(String path) {
        return path.endsWith("package-lock.json") || path.endsWith("yarn.lock") || path.endsWith("pnpm-lock.yaml") ||
                path.endsWith(".min.js") || path.contains("/dist/") || path.contains("/vendor/");
    }

    private static boolean isProviderVariable(String variable, WordPool.Provider provider) {
        String n = normalize(variable);
        return provider.envVars().stream().map(CandidateExtractor::normalize).anyMatch(n::contains);
    }

    private boolean isCredentialVariable(String variable) {
        String n = normalize(variable);
        if (n.contains("apikey") || n.contains("token") || n.contains("secret") ||
                n.endsWith("accesskey") || n.contains("credentialkey") || n.endsWith("authkey")) return true;
        return credentialVariableSignals.stream().anyMatch(signal -> n.equals(signal) || n.endsWith(signal));
    }

    private static boolean looksLikeCredentialVariable(String value) {
        String n = normalize(value);
        return !n.contains("baseurl") && !n.contains("endpoint") && !n.equals("apiurl") &&
                (n.contains("key") || n.contains("token") || n.contains("secret") || n.contains("credential"));
    }

    private static boolean isCandidateValue(String value) {
        if (value.length() < 20 || value.length() > 512) return false;
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) return false;
        if (value.contains("${") || value.contains("{{") || value.contains("<%") || value.startsWith("$")) return false;
        if (value.chars().anyMatch(Character::isWhitespace)) return false;
        return value.matches("[A-Za-z0-9_./+\\-=]+") && hasCredentialShape(value);
    }

    private static boolean hasCredentialShape(String value) {
        boolean letter = value.chars().anyMatch(Character::isLetter);
        boolean digit = value.chars().anyMatch(Character::isDigit);
        long distinct = value.chars().distinct().count();
        return letter && digit && distinct >= 8;
    }

    private static boolean isPlaceholder(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        for (String marker : PLACEHOLDER_MARKERS) if (lower.contains(marker)) return true;
        if (lower.matches("(?:sk[-_])?x{8,}")) return true;
        if (value.chars().distinct().count() < 8) return true;
        return lower.contains("localhost") || lower.contains("example.com");
    }

    private LocatedUrl nearestProviderUrl(List<LocatedUrl> urls, int line, WordPool.Provider provider) {
        LocatedUrl best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (LocatedUrl url : urls) {
            String lower = url.url().toLowerCase(Locale.ROOT);
            boolean target = pool.endpoints().stream().map(String::toLowerCase)
                    .map(CandidateExtractor::hostOnly).anyMatch(lower::contains);
            if (!target) continue;
            if (provider != null && !WordPool.matchesProvider(lower, provider.slug())) continue;
            int distance = Math.abs(url.line() - line);
            if (distance < bestDistance) { bestDistance = distance; best = url; }
        }
        return bestDistance <= 40 ? best : null;
    }

    private static List<LocatedUrl> locateUrls(String content) {
        List<LocatedUrl> urls = new ArrayList<>();
        Matcher matcher = URL.matcher(content);
        while (matcher.find()) urls.add(new LocatedUrl(matcher.group(), lineAt(content, matcher.start())));
        return urls;
    }

    private static int lineAt(String content, int offset) {
        int line = 1;
        for (int i = 0; i < Math.min(offset, content.length()); i++) if (content.charAt(i) == '\n') line++;
        return line;
    }

    private static String trimValue(String value) {
        String v = value.trim();
        while (!v.isEmpty() && "\"'`);,}".indexOf(v.charAt(v.length() - 1)) >= 0) v = v.substring(0, v.length() - 1);
        return v;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String hostOnly(String value) {
        return value.replaceFirst("(?i)^https?://", "").replaceFirst("/.*$", "");
    }

    private static String mask(String value) {
        if (value.length() <= 8) return "*".repeat(value.length());
        int head = Math.min(5, value.length() / 3);
        int tail = Math.min(4, value.length() / 4);
        return value.substring(0, head) + "*".repeat(Math.min(16, value.length() - head - tail)) + value.substring(value.length() - tail);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : digest) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String severity(int score) {
        if (score >= 90) return "critical";
        if (score >= 70) return "high";
        if (score >= 40) return "medium";
        return "low";
    }

    private record RawCandidate(String variable, String value, int offset) {}
    private record LocatedUrl(String url, int line) {}
    private record Score(int value, List<String> reasons) {}
}




