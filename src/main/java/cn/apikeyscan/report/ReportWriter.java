package cn.apikeyscan.report;

import cn.apikeyscan.scan.Finding;
import cn.apikeyscan.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReportWriter {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private ReportWriter() {}

    public static Paths write(Path outputDir, ReportSummary summary, List<Finding> findings) throws IOException {
        Files.createDirectories(outputDir);
        String stamp = FILE_TIME.format(summary.finishedAt());
        Path jsonPath = outputDir.resolve("ApiKey_Scan-report-" + stamp + ".json");
        Path markdownPath = outputDir.resolve("ApiKey_Scan-report-" + stamp + ".md");

        List<Finding> sorted = findings.stream()
                .sorted(Comparator.comparingInt(Finding::score).reversed()
                        .thenComparing(Finding::repository).thenComparing(Finding::path).thenComparingInt(Finding::line))
                .toList();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("metadata", summary.toMap());
        root.put("findings", sorted.stream().map(Finding::toMap).toList());
        root.put("notice", "报告中的凭据值已脱敏；value_fingerprint 是 SHA-256，用于去重，不可还原原始值。");
        Json.write(jsonPath, root);
        Files.writeString(markdownPath, markdown(summary, sorted), StandardCharsets.UTF_8);
        return new Paths(jsonPath, markdownPath);
    }

    private static String markdown(ReportSummary s, List<Finding> findings) {
        StringBuilder out = new StringBuilder();
        out.append("# ApiKey_Scan - GitHub API Key 泄露扫描报告\n\n");
        out.append("> Markdown 报告会按 `baseurl:<Base URL>;apikey:<API Key>` 格式明文输出候选凭据，仅用于已获授权的内部验证；请严格限制报告访问权限，不要将其提交到版本库、日志或公开位置，也不要尝试调用或验证发现的 Key。\n\n");
        out.append("## 扫描概览\n\n");
        out.append("- 开始时间：`").append(s.startedAt()).append("`\n");
        out.append("- 结束时间：`").append(s.finishedAt()).append("`\n");
        out.append("- 上次成功扫描：`").append(blank(s.previousSuccessfulScan())).append("`\n");
        out.append("- 查询数：").append(s.queriesExecuted()).append(" / ").append(s.queriesPlanned()).append("\n");
        out.append("- 搜索结果：").append(s.searchItems()).append("\n");
        out.append("- 下载文件：").append(s.downloadedFiles()).append("\n");
        out.append("- 增量跳过：").append(s.skippedObjects()).append("\n");
        out.append("- 重复内容跳过：").append(s.duplicateContents()).append("\n");
        out.append("- 高风险发现：").append(findings.size()).append("\n");
        out.append("- 错误数：").append(s.errors().size()).append("\n\n");

        out.append("## 发现项\n\n");
        if (findings.isEmpty()) {
            out.append("未发现达到评分阈值的候选凭据。\n");
        } else {
            out.append("| 分数 | 厂商 | 泄露文件 URL | 行数 | Base URL / API Key（明文） |\n");
            out.append("|---:|---|---|---:|---|\n");
            for (Finding f : findings) {
                out.append('|').append(f.score()).append('|')
                        .append(escape(f.provider())).append('|')
                        .append('<').append(escapeUrl(f.htmlUrl())).append(">|")
                        .append(f.line()).append('|')
                        .append('`').append(escapeCode("baseurl:" + f.baseUrl() + ";apikey:" + f.rawValue())).append("`|\n");
            }
        }

        if (!s.errors().isEmpty()) {
            out.append("## 错误\n\n");
            for (String error : s.errors()) out.append("- ").append(escape(error)).append('\n');
        }
        return out.toString();
    }

    private static String blank(String value) { return value == null || value.isBlank() ? "无" : value; }
    private static String escape(String value) { return value == null ? "" : value.replace("|", "\\|").replace("\n", " "); }
    private static String escapeCode(String value) { return value == null ? "" : value.replace("`", "'").replace("|", "\\|"); }
    private static String escapeUrl(String value) { return value == null ? "" : value.replace(" ", "%20").replace(">", "%3E"); }

    public record Paths(Path json, Path markdown) {}

    public record ReportSummary(
            Instant startedAt,
            Instant finishedAt,
            String previousSuccessfulScan,
            int queriesPlanned,
            int queriesExecuted,
            int searchItems,
            int downloadedFiles,
            int skippedObjects,
            int duplicateContents,
            List<String> errors
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("started_at", startedAt.toString());
            map.put("finished_at", finishedAt.toString());
            map.put("previous_successful_scan", previousSuccessfulScan);
            map.put("queries_planned", queriesPlanned);
            map.put("queries_executed", queriesExecuted);
            map.put("search_items", searchItems);
            map.put("downloaded_files", downloadedFiles);
            map.put("skipped_unchanged_objects", skippedObjects);
            map.put("duplicate_contents", duplicateContents);
            map.put("errors", new ArrayList<>(errors));
            return map;
        }
    }
}

