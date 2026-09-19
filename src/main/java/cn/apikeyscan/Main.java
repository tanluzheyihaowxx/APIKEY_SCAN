package cn.apikeyscan;

import cn.apikeyscan.config.WordPool;
import cn.apikeyscan.github.GitHubClient;
import cn.apikeyscan.github.SearchItem;
import cn.apikeyscan.report.ReportWriter;
import cn.apikeyscan.scan.CandidateExtractor;
import cn.apikeyscan.scan.Finding;
import cn.apikeyscan.scan.QueryBuilder;
import cn.apikeyscan.state.ScanState;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Main {
    public static void main(String[] args) {
        try {
            Options options = Options.parse(args);
            run(options);
        } catch (Options.HelpRequested ignored) {
            System.out.println(Options.help());
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            if (System.getenv("DEBUG") != null) e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void run(Options options) throws Exception {
        WordPool pool = WordPool.load(options.root());
        List<QueryBuilder.QuerySpec> queries = QueryBuilder.build(pool, options.maxQueries(), options.extraQueries());
        System.out.printf(Locale.ROOT, "加载词池: %d 个厂商，%d 个 Key/BaseURL 词，%d 个 Endpoint，计划 %d 条查询%n",
                pool.providers().size(), pool.keyWords().size(), pool.endpoints().size(), queries.size());

        if (options.dryRun()) {
            System.out.println("\n--- GitHub Code Search 查询计划 ---");
            int i = 1;
            for (QueryBuilder.QuerySpec query : queries) {
                System.out.printf(Locale.ROOT, "%02d. [%s/%s] %s%n", i++, query.providerSlug(), query.category(), query.query());
            }
            return;
        }

        Instant startedAt = Instant.now();
        ScanState state = ScanState.load(options.stateFile());
        String previousSuccessfulScan = state.lastSuccessfulScan();
        GitHubClient github = new GitHubClient(options.token(), options.requestDelayMs());
        CandidateExtractor extractor = new CandidateExtractor(pool);
        List<Finding> findings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int queriesExecuted = 0;
        int searchItems = 0;
        int downloadedFiles = 0;
        int skippedObjects = 0;
        int duplicateContents = 0;

        System.out.println("上次成功扫描: " + (previousSuccessfulScan.isBlank() ? "无" : previousSuccessfulScan));
        for (int queryIndex = 0; queryIndex < queries.size(); queryIndex++) {
            QueryBuilder.QuerySpec query = queries.get(queryIndex);
            System.out.printf(Locale.ROOT, "[%d/%d] 搜索: %s%n", queryIndex + 1, queries.size(), query.query());
            boolean queryFailed = false;
            queriesExecuted++;
            int processedForQuery = 0;
            int page = 1;

            while (processedForQuery < options.maxResultsPerQuery()) {
                GitHubClient.SearchPage result;
                try {
                    result = github.searchCode(query.query(), page, options.perPage());
                } catch (Exception e) {
                    String message = "查询失败 [" + query.query() + "]: " + e.getMessage();
                    System.err.println(message);
                    errors.add(message);
                    queryFailed = true;
                    break;
                }
                if (result.incomplete()) System.err.println("  注意: GitHub 标记本次搜索结果不完整");
                if (result.items().isEmpty()) break;

                for (SearchItem item : result.items()) {
                    if (processedForQuery >= options.maxResultsPerQuery()) break;
                    processedForQuery++;
                    searchItems++;
                    if (!options.includeForks() && item.fork()) continue;
                    if (state.hasObject(item.objectKey())) {
                        skippedObjects++;
                        continue;
                    }

                    try {
                        String content = github.downloadContent(item);
                        downloadedFiles++;
                        String contentHash = sha256(content);
                        state.addObject(item.objectKey());
                        if (state.hasContent(contentHash)) {
                            duplicateContents++;
                            continue;
                        }
                        state.addContent(contentHash);
                        List<Finding> fileFindings = extractor.extract(item, content, query, options.minScore());
                        findings.addAll(fileFindings);
                        if (!fileFindings.isEmpty()) {
                            System.out.printf(Locale.ROOT, "  命中 %d 项: %s/%s%n", fileFindings.size(), item.repository(), item.path());
                        }
                    } catch (Exception e) {
                        String message = "文件处理失败 [" + item.repository() + "/" + item.path() + "]: " + e.getMessage();
                        System.err.println("  " + message);
                        errors.add(message);
                    }
                }

                if (result.items().size() < options.perPage()) break;
                page++;
                if ((page - 1L) * options.perPage() >= 1000) break;
            }
            state.save(options.stateFile());
            if (queryFailed) continue;
        }

        if (errors.isEmpty()) state.markSuccessfulNow();
        state.save(options.stateFile());
        Instant finishedAt = Instant.now();
        ReportWriter.ReportSummary summary = new ReportWriter.ReportSummary(
                startedAt, finishedAt, previousSuccessfulScan, queries.size(), queriesExecuted,
                searchItems, downloadedFiles, skippedObjects, duplicateContents, List.copyOf(errors));
        ReportWriter.Paths paths = ReportWriter.write(options.outputDir(), summary, findings);

        System.out.println("\n扫描完成");
        System.out.println("发现项: " + findings.size());
        System.out.println("JSON 报告: " + paths.json().toAbsolutePath());
        System.out.println("Markdown 报告: " + paths.markdown().toAbsolutePath());
        System.out.println("增量状态: " + options.stateFile().toAbsolutePath());
        if (!errors.isEmpty()) System.out.println("注意: 本次有 " + errors.size() + " 个错误，未更新 last_successful_scan。已成功处理的对象仍已保存用于增量去重。");
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
}


