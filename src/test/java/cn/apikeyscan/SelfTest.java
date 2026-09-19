package cn.apikeyscan;

import cn.apikeyscan.config.WordPool;
import cn.apikeyscan.github.SearchItem;
import cn.apikeyscan.report.ReportWriter;
import cn.apikeyscan.scan.CandidateExtractor;
import cn.apikeyscan.scan.Finding;
import cn.apikeyscan.scan.QueryBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public final class SelfTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        WordPool pool = WordPool.load(root);
        CandidateExtractor extractor = new CandidateExtractor(pool);

        assertHit(extractor, "deepseek", ".env", """
                DEEPSEEK_API_KEY="sk-0123456789abcdef0123456789abcdef"
                DEEPSEEK_BASE_URL="https://api.deepseek.com/v1"
                """);
        assertHit(extractor, "aliyun", "config.yml", """
                dashscopeApiKey: sk-abcdef0123456789abcdef0123456789
                baseUrl: https://dashscope.aliyuncs.com/compatible-mode/v1
                """);
        assertHit(extractor, "moonshot", "App.java", """
                String moonshotApiKey = "sk-9876543210abcdef9876543210abcdef";
                String baseUrl = "https://api.moonshot.cn/v1";
                """);

        List<Finding> placeholder = extract(extractor, "deepseek", "README.md", """
                DEEPSEEK_API_KEY="YOUR_API_KEY_HERE_1234567890"
                DEEPSEEK_BASE_URL="https://api.deepseek.com/v1"
                """, 70);
        if (!placeholder.isEmpty()) throw new AssertionError("占位符不应达到高风险阈值");

        List<QueryBuilder.QuerySpec> queries = QueryBuilder.build(pool, 100, List.of());
        if (queries.stream().noneMatch(q -> q.query().contains("DEEPSEEK_API_KEY"))) throw new AssertionError("缺少 DeepSeek 查询");
        if (queries.stream().noneMatch(q -> q.query().contains("dashscope.aliyuncs.com"))) throw new AssertionError("缺少百炼 endpoint 查询");
        if (queries.stream().noneMatch(q -> q.query().contains("api.moonshot.cn"))) throw new AssertionError("缺少 Moonshot endpoint 查询");

        assertMarkdownReport(extractor);

        System.out.println("SelfTest 通过: 词池加载、查询生成、三家厂商提取、评分、占位符降权和 Markdown 报告格式正常。");
    }

    private static void assertHit(CandidateExtractor extractor, String slug, String path, String content) {
        List<Finding> findings = extract(extractor, slug, path, content, 70);
        if (findings.isEmpty()) throw new AssertionError(slug + " 未提取到高风险候选");
        if (findings.stream().noneMatch(f -> f.providerSlug().equals(slug))) throw new AssertionError(slug + " 厂商识别失败");
        if (findings.stream().anyMatch(f -> f.maskedValue().contains("0123456789abcdef0123456789abcdef"))) {
            throw new AssertionError("报告值未脱敏");
        }
    }

    private static List<Finding> extract(CandidateExtractor extractor, String slug, String path, String content, int minScore) {
        SearchItem item = new SearchItem(1, "owner/repo", false, path, "abc123", "https://api.github.invalid/content", "https://github.com/owner/repo/blob/abc/" + path);
        return extractor.extract(item, content, new QueryBuilder.QuerySpec(slug, slug, "test"), minScore);
    }

    private static void assertMarkdownReport(CandidateExtractor extractor) throws Exception {
        List<Finding> findings = extract(extractor, "deepseek", ".env", """
                DEEPSEEK_API_KEY="sk-0123456789abcdef0123456789abcdef"
                DEEPSEEK_BASE_URL="https://api.deepseek.com/v1"
                """, 70);
        Instant now = Instant.parse("2026-09-19T00:00:00Z");
        Path output = Files.createTempDirectory("ApiKey_Scan-report-test-");
        ReportWriter.Paths paths = ReportWriter.write(output, new ReportWriter.ReportSummary(
                now, now, null, 1, 1, 1, 1, 0, 0, List.of()), findings);
        String markdown = Files.readString(paths.markdown(), StandardCharsets.UTF_8);
        String expectedHeader = "| 分数 | 厂商 | 泄露文件 URL | 行数 | Base URL / API Key（明文） |";
        if (!markdown.contains(expectedHeader)) throw new AssertionError("Markdown 表头不符合预期");
        if (!markdown.contains("<https://github.com/owner/repo/blob/abc/.env#L1>")) throw new AssertionError("Markdown 缺少可直接访问的文件 URL");
        if (!markdown.contains("`baseurl:https://api.deepseek.com/v1;apikey:sk-0123456789abcdef0123456789abcdef`")) throw new AssertionError("Markdown 缺少明文 Base URL/API Key");
        if (!markdown.contains(findings.getFirst().rawValue())) throw new AssertionError("Markdown 缺少明文 API Key");
        if (markdown.contains("API Key（脱敏）")) throw new AssertionError("Markdown 不应使用脱敏字段标题");
        if (markdown.contains("| 风险 |") || markdown.contains("## 评分依据")) throw new AssertionError("Markdown 仍包含旧版发现项格式");
    }
}

