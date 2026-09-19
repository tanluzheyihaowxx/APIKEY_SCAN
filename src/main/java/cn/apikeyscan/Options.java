package cn.apikeyscan;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public record Options(
        Path root,
        Path outputDir,
        Path stateFile,
        String token,
        int minScore,
        int maxQueries,
        int maxResultsPerQuery,
        int perPage,
        long requestDelayMs,
        boolean dryRun,
        boolean includeForks,
        List<String> extraQueries
) {
    public static Options parse(String[] args) {
        Path root = Path.of(".").toAbsolutePath().normalize();
        Path output = root.resolve("reports");
        Path state = root.resolve(".ApiKey_Scan").resolve("state.json");
        String token = firstNonBlank(System.getenv("GITHUB_TOKEN"), System.getenv("GH_TOKEN"));
        int minScore = 70;
        int maxQueries = 60;
        int maxResults = 30;
        int perPage = 30;
        long delayMs = 6500;
        boolean dryRun = false;
        boolean includeForks = false;
        List<String> extraQueries = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--root" -> root = Path.of(requireValue(args, ++i, arg)).toAbsolutePath().normalize();
                case "--output" -> output = Path.of(requireValue(args, ++i, arg)).toAbsolutePath().normalize();
                case "--state" -> state = Path.of(requireValue(args, ++i, arg)).toAbsolutePath().normalize();
                case "--token" -> token = requireValue(args, ++i, arg);
                case "--min-score" -> minScore = parseInt(args, ++i, arg, 0, 100);
                case "--max-queries" -> maxQueries = parseInt(args, ++i, arg, 1, 1000);
                case "--max-results-per-query" -> maxResults = parseInt(args, ++i, arg, 1, 1000);
                case "--per-page" -> perPage = parseInt(args, ++i, arg, 1, 100);
                case "--delay-ms" -> delayMs = parseLong(args, ++i, arg, 0, 600_000);
                case "--query" -> extraQueries.add(requireValue(args, ++i, arg));
                case "--dry-run" -> dryRun = true;
                case "--include-forks" -> includeForks = true;
                case "--help", "-h" -> throw new HelpRequested();
                default -> throw new IllegalArgumentException("未知参数: " + arg);
            }
        }

        if (output.equals(Path.of(".").toAbsolutePath().normalize().resolve("reports"))) output = root.resolve("reports");
        if (state.equals(Path.of(".").toAbsolutePath().normalize().resolve(".ApiKey_Scan").resolve("state.json"))) {
            state = root.resolve(".ApiKey_Scan").resolve("state.json");
        }
        if (!dryRun && (token == null || token.isBlank())) {
            throw new IllegalArgumentException("缺少 GitHub Token。请设置环境变量 GITHUB_TOKEN，或使用 --token。Token 不会写入报告。");
        }
        return new Options(root, output, state, token, minScore, maxQueries, maxResults, perPage,
                delayMs, dryRun, includeForks, List.copyOf(extraQueries));
    }

    public static String help() {
        return """
                ApiKey_Scan - GitHub API Key 泄露扫描器

                用法:
                  java -jar ApiKey_Scan.jar [选项]

                认证:
                  推荐设置环境变量 GITHUB_TOKEN；也可使用 --token <token>。

                选项:
                  --root <dir>                   词池根目录，默认当前目录
                  --output <dir>                 报告目录，默认 <root>/reports
                  --state <file>                 增量状态文件，默认 <root>/.ApiKey_Scan/state.json
                  --min-score <0-100>            报告阈值，默认 70
                  --max-queries <n>              单次最大查询数，默认 60
                  --max-results-per-query <n>    每条查询最多处理结果数，默认 30
                  --per-page <1-100>             GitHub 每页结果数，默认 30
                  --delay-ms <ms>                搜索请求间隔，默认 6500
                  --query <query>                追加自定义 GitHub Code Search 查询，可重复
                  --include-forks                不过滤 fork 仓库
                  --dry-run                      仅生成查询，不访问 GitHub
                  --help                         显示帮助
                """;
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) throw new IllegalArgumentException(option + " 缺少值");
        return args[index];
    }

    private static int parseInt(String[] args, int index, String option, int min, int max) {
        int value;
        try { value = Integer.parseInt(requireValue(args, index, option)); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(option + " 必须是整数"); }
        if (value < min || value > max) throw new IllegalArgumentException(option + " 必须在 " + min + " 到 " + max + " 之间");
        return value;
    }

    private static long parseLong(String[] args, int index, String option, long min, long max) {
        long value;
        try { value = Long.parseLong(requireValue(args, index, option)); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(option + " 必须是整数"); }
        if (value < min || value > max) throw new IllegalArgumentException(option + " 必须在 " + min + " 到 " + max + " 之间");
        return value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }

    public static final class HelpRequested extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}



