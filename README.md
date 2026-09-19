# GitHub 国内大模型 API Key 泄露扫描器

一个基于 **Java 21 标准库** 的 GitHub 公共代码敏感凭据排查工具，面向获得授权的安全审计、组织自有资产排查和公开泄露响应场景。

工具通过 GitHub Code Search 搜索与 API Key、Token、Secret、Base URL、配置文件和厂商关键词相关的上下文，再下载候选文件进行本地提取、评分、去重和报告生成。它不会调用、消费或验证发现的凭据。

> **重要：仅限授权使用。**
> 
> 本项目用于防御性安全审计。请只扫描你有权处理的公开代码、组织资产或已明确授权的目标。不要利用本工具访问第三方服务、测试凭据有效性、尝试登录或扩大访问范围。

## 目录

* [项目特性](#项目特性)
* [支持的厂商](#支持的厂商)
* [工作流程](#工作流程)
* [安全与隐私说明](#安全与隐私说明)
* [环境要求](#环境要求)
* [快速开始](#快速开始)
* [命令行参数](#命令行参数)
* [输出文件](#输出文件)
* [报告格式](#报告格式)
* [风险评分](#风险评分)
* [增量扫描与去重](#增量扫描与去重)
* [词池与自定义查询](#词池与自定义查询)
* [构建与测试](#构建与测试)
* [Windows 便携版](#windows-便携版)
* [项目结构](#项目结构)
* [限制与误报](#限制与误报)
* [故障排查](#故障排查)
* [贡献指南](#贡献指南)
* [许可证](#许可证)

## 项目特性

* **无第三方 Java 依赖**：核心程序使用 Java 21 标准库构建。
* **上下文搜索**：不直接依赖裸 `sk-...` 字符串搜索，而是结合变量名、配置文件、调用方式、厂商关键词和 Base URL 生成查询。
* **多种代码形式提取**：支持常见赋值、函数调用和 XML 标签形式的候选值提取。
* **多厂商识别**：根据变量名、附近文本、Base URL 和查询上下文识别候选厂商。
* **风险评分**：综合变量名、文件类型、值形态、厂商上下文、Base URL、文档/测试路径和占位符等因素评分。
* **双重去重**：基于 GitHub 对象标识和文件内容 SHA-256 去重，减少重复仓库、镜像和 fork 的重复处理。
* **增量扫描**：保存扫描状态，后续运行可跳过已经处理过的相同对象和相同内容。
* **JSON + Markdown 报告**：JSON 适合程序化处理，Markdown 适合人工复核和审计留档。
* **GitHub 限流处理**：支持请求间隔配置，并在 GitHub 返回限流相关响应时根据响应头退避重试。
* **Windows 便携版**：发布包内置精简 Java 运行时，无需另行安装 Java 或 Maven。

## 支持的厂商

当前内置词池包含：

| 厂商  | 典型变量名 | 典型 Base URL / 关键词 |
| --- | --- | --- |
| 阿里云百炼 / DashScope / 通义千问 | `DASHSCOPE_API_KEY`、`QWEN_API_KEY`、`BAILIAN_API_KEY` | `dashscope`、`qwen`、`百炼`、`通义千问` |
| DeepSeek | `DEEPSEEK_API_KEY`、`DEEPSEEK_KEY` | `deepseek`、`api.deepseek.com` |
| 月之暗面 / Kimi | `MOONSHOT_API_KEY`、`KIMI_API_KEY` | `moonshot`、`kimi`、`api.moonshot.cn` |

词池定义位于：

    providers/aliyun.json
    providers/deepseek.json
    providers/moonshot.json
    common/key_words.json
    common/config_files.json
    common/endpoint.json

项目结构支持继续添加其他厂商，但新增厂商时应同时补充变量名、关键词、Endpoint 和测试用例。

## 工作流程

    词池 JSON
       │
       ▼
    生成 GitHub Code Search 查询
       │
       ▼
    搜索变量名、配置文件、调用方式、Base URL 上下文
       │
       ▼
    下载候选文件
       │
       ▼
    本地提取候选变量和值
       │
       ▼
    厂商识别、占位符判断、风险评分
       │
       ▼
    对象去重 + 内容去重 + 增量状态更新
       │
       ▼
    生成 JSON 和 Markdown 报告

默认情况下，fork 仓库会被过滤；使用 `--include-forks` 可以显式包含 fork 仓库。

## 安全与隐私说明

### GitHub Token

工具需要 GitHub Token 调用 GitHub API。推荐通过环境变量传入：

    $env:GITHUB_TOKEN = '你的 GitHub Token'

也支持 `GH_TOKEN`：

    $env:GH_TOKEN = '你的 GitHub Token'

注意事项：

* 使用满足实际需求的最小权限 Token。
* 扫描公开代码时，不要授予仓库写入权限。
* 不要把 Token 写入仓库、脚本、截图、Issue、日志或报告。
* `GITHUB_TOKEN` / `GH_TOKEN` 不会写入扫描状态或报告。
* 可以使用 `--token` 传入 Token，但命令行参数可能出现在进程列表或终端历史中，不推荐用于长期运行环境。

### 报告中的候选凭据

当前版本的 Markdown 报告会将候选凭据按以下形式明文输出，以便在已授权环境中人工核对：

    baseurl:<Base URL>;apikey:<API Key>

例如：

    baseurl:https://dashscope.aliyuncs.com/compatible-mode/v1;apikey:sk-example-not-a-real-key

因此：

* Markdown 报告应按照敏感信息处理。
* 不要把 `reports/` 目录提交到公开仓库。
* 不要把 Markdown 报告上传到公共 Issue、Pull Request、制品仓库或聊天群。
* 需要自动化处理或长期留档时，优先使用 JSON 报告；JSON 仅保存脱敏值、长度和 SHA-256 指纹。
* 工具不会调用、消费、登录或验证候选 API Key。
* 发现疑似真实凭据后，应通过正规流程通知仓库所有者或厂商，并推动撤销、轮换和泄露处置。

### 开源前检查清单

在将项目推送到 GitHub 前，建议至少执行：

    git status --short
    Get-ChildItem -File -Recurse .\reports -ErrorAction SilentlyContinue
    Get-ChildItem -File -Recurse | Select-String -Pattern 'sk-[A-Za-z0-9_-]{16,}|GITHUB_TOKEN|github_pat_' -SimpleMatch:$false

确认以下内容没有被提交：

* 真实 API Key、GitHub Token 或其他凭据；
* 扫描生成的 JSON/Markdown 报告；
* `.github-leak-scanner/state.json`；
* 包含敏感内容的临时文件、日志或压缩包。

## 环境要求

### 从源码构建

* Windows、Linux 或 macOS 均可运行核心 Java 程序；
* JDK 21 或更高版本；
* PowerShell（Windows 构建脚本使用）；
* GitHub Token；
* 可访问 GitHub API 的网络环境。

### 使用 Windows 便携版

Windows x64 便携版已经内置 Java 运行时，使用者不需要单独安装 Java 或 Maven。详见[Windows 便携版](#windows-便携版)。

## 快速开始

### 方式一：从源码构建并运行

克隆仓库后进入项目目录：

    git clone <你的 GitHub 仓库地址>
    cd github-api-leak-scanner

设置 Token：

    $env:GITHUB_TOKEN = '你的 GitHub Token'

构建：

    .\build.ps1

构建结果：

    build/github-api-leak-scanner.jar

先查看查询计划，不访问 GitHub：

    .\run.ps1 --dry-run

执行小范围扫描：

    .\run.ps1 `
      --max-queries 3 `
      --max-results-per-query 5 `
      --min-score 70

执行默认扫描：

    .\run.ps1

### 方式二：直接运行 JAR

如果已经安装 JDK 21，也可以直接运行：

    java -jar .\build\github-api-leak-scanner.jar --dry-run

正式扫描：

    java -jar .\build\github-api-leak-scanner.jar `
      --root . `
      --output .\reports `
      --state .\.github-leak-scanner\state.json

### 方式三：Windows 便携包

完整解压发布包后，在解压目录执行：

    $env:GITHUB_TOKEN = '你的 GitHub Token'
    .\run.ps1 --max-queries 3 --max-results-per-query 5

也可以使用 CMD：

    set GITHUB_TOKEN=你的GitHubToken
    run.bat --max-queries 3 --max-results-per-query 5

## 命令行参数

通过启动脚本运行时，参数会原样传递给 Java 程序。

| 参数  | 默认值 | 说明  |
| --- | --- | --- |
| `--root <dir>` | 当前目录 | 词池根目录。启动脚本会自动使用脚本所在目录。 |
| `--output <dir>` | `<root>/reports` | JSON 和 Markdown 报告输出目录。 |
| `--state <file>` | `<root>/.github-leak-scanner/state.json` | 增量扫描状态文件。 |
| `--token <token>` | `GITHUB_TOKEN` 或 `GH_TOKEN` | 通过命令行传入 Token，不推荐。 |
| `--min-score <0-100>` | `70` | 报告阈值，低于该分数的候选项不会写入报告。 |
| `--max-queries <1-1000>` | `60` | 单次运行最多执行的查询数量。 |
| `--max-results-per-query <1-1000>` | `30` | 每条查询最多处理的搜索结果数。 |
| `--per-page <1-100>` | `30` | GitHub 搜索 API 每页结果数。 |
| `--delay-ms <0-600000>` | `6500` | 搜索请求之间的等待时间，单位为毫秒。 |
| `--query <query>` | 无   | 追加自定义 GitHub Code Search 查询，可重复使用。 |
| `--include-forks` | 关闭  | 包含 fork 仓库，否则默认跳过 fork。 |
| `--dry-run` | 关闭  | 只生成并打印查询计划，不访问 GitHub。 |
| `--help` / `-h` | —   | 显示帮助信息。 |

### 常用命令示例

降低报告阈值：

    .\run.ps1 --min-score 60

限制查询和结果数量：

    .\run.ps1 --max-queries 10 --max-results-per-query 20

增加请求间隔：

    .\run.ps1 --delay-ms 10000

包含 fork 仓库：

    .\run.ps1 --include-forks

追加自定义查询：

    .\run.ps1 `
      --query 'deepseek filename:.env' `
      --query 'dashscope apiKey language:Java'

使用新的状态文件执行重新分析：

    .\run.ps1 --state '.github-leak-scanner\rescan-state.json'

将报告输出到指定目录：

    .\run.ps1 --output 'D:\audit-reports\github-leaks'

## 输出文件

默认输出结构：

    reports/
    ├─ github-api-leak-report-YYYYMMDD-HHmmss.json
    └─ github-api-leak-report-YYYYMMDD-HHmmss.md
    
    .github-leak-scanner/
    └─ state.json

### JSON 报告

JSON 报告包含扫描元数据和发现项。典型字段包括：

    {
      "provider": "DeepSeek",
      "repository": "owner/repository",
      "path": ".env",
      "source_url": "https://github.com/owner/repository/blob/<sha>/.env#L1",
      "line": 1,
      "variable": "DEEPSEEK_API_KEY",
      "masked_value": "sk-01****************cdef",
      "value_fingerprint": "<sha256>",
      "value_length": 36,
      "base_url": "https://api.deepseek.com/v1",
      "score": 100,
      "severity": "critical"
    }

JSON 中的 `value_fingerprint` 是 SHA-256 指纹，用于去重和关联，不可还原原始 Key。

### Markdown 报告

Markdown 报告适合人工查看，包含：

* 扫描起止时间；
* 查询数量、搜索结果数量、下载文件数量；
* 增量跳过和重复内容跳过数量；
* 高风险发现数量和错误数量；
* 厂商、来源文件 URL、命中行号、风险分数；
* `baseurl:<Base URL>;apikey:<API Key>` 明文候选凭据。

示例表格结构：

    | 分数 | 厂商 | 泄露文件 URL | 行数 | Base URL / API Key（明文） |
    |---:|---|---|---:|---|
    | 100 | DeepSeek | <https://github.com/example/repo/blob/main/.env#L1> | 1 | `baseurl:https://api.deepseek.com/v1;apikey:sk-example-not-a-real-key` |

## 风险评分

评分范围为 `0–100`，默认只报告 `score >= 70` 的候选项。

| 评分条件 | 分值  |
| --- | --- |
| 变量名包含 `API_KEY` / `APIKEY` | +40 |
| 变量名包含 `SECRET` | +30 |
| 变量名包含 `TOKEN` | +20 |
| 位于 `.env` 或常见配置文件 | +20 |
| 候选值长度和结构符合凭据特征 | +20 |
| 命中厂商专属变量名 | +15 |
| 附近存在目标厂商 Base URL | +20 |
| 命中常见 Key 前缀 | +10 |
| README、说明文档或示例路径 | -30 |
| 测试、fixture 或 mock 路径 | -20 |
| 占位符、示例值或测试值 | -45 |
| 生成文件或锁文件 | -25 |

最终分数会限制在 `0–100`：

| 分数  | 严重级别 |
| --- | --- |
| `90–100` | `critical` |
| `70–89` | `high` |
| `40–69` | `medium` |
| `0–39` | `low` |

评分是候选优先级提示，不等同于凭据一定有效，也不等同于漏洞确认。

## 增量扫描与去重

工具在状态文件中保存以下信息：

    repository_id | file_path | blob_sha
    SHA-256(file_content)
    last_successful_scan

再次运行相同查询时：

* 相同仓库、路径和 `blob_sha` 的对象会跳过；
* fork、镜像或复制仓库中的相同内容会通过内容哈希跳过；
* 文件内容变化后 `blob_sha` 改变，会重新下载和分析；
* 只有没有错误的运行才会更新 `last_successful_scan`；
* 已成功处理的对象即使本次其他查询出错，也会保留在状态文件中用于后续去重。

如果需要从头重新分析，可以指定新的状态文件：

    .\run.ps1 --state '.github-leak-scanner\new-state.json'

## 词池与自定义查询

### 词池文件

| 文件  | 作用  |
| --- | --- |
| `providers/*.json` | 厂商名称、环境变量和厂商关键词。 |
| `common/key_words.json` | API Key、Token、Secret、Base URL 等通用变量名。 |
| `common/config_files.json` | 常见配置文件名，如 `.env`、YAML、JSON 和 Python 配置文件。 |
| `common/endpoint.json` | 目标厂商 Endpoint 和 Base URL。 |

修改词池 JSON 后无需修改 Java 代码；重新运行即可加载最新词池。建议修改后先执行：

    .\run.ps1 --dry-run

### 自定义查询

`--query` 会在自动生成的查询之外追加 GitHub Code Search 查询。例如：

    .\run.ps1 --query 'dashscope apiKey language:Java'

自定义查询适合补充特定语言、目录、配置文件名或组织范围，但查询结果仍会经过本地候选提取和评分，不会因为命中搜索结果就直接写入报告。

## 构建与测试

### 构建

    .\build.ps1

构建结果：

    build/github-api-leak-scanner.jar

### 测试

    .\test.ps1

测试覆盖当前核心能力：

* 词池加载；
* 查询生成；
* 阿里云、DeepSeek、Kimi 相关候选提取；
* `.env`、YAML、Java 等常见赋值形式；
* Base URL 上下文加分；
* 占位符降权；
* Markdown 明文格式输出；
* JSON 中的脱敏值和指纹字段。

### 直接查看帮助

    java -jar .\build\github-api-leak-scanner.jar --help

## Windows 便携版

仓库中的便携包适合不希望单独安装 Java 的 Windows x64 用户。便携包通常包含：

    github-api-leak-scanner-portable-win-x64-*/
    ├─ github-api-leak-scanner.jar
    ├─ runtime/                  # 内置 Java 运行时
    ├─ common/
    ├─ providers/
    ├─ run.ps1
    ├─ run.bat
    ├─ dry-run.bat
    ├─ README.md
    ├─ manifest.json
    └─ SHA256SUMS.txt

使用步骤：

1. 从 GitHub Releases 下载 ZIP；
2. 完整解压；
3. 在解压目录打开 PowerShell；
4. 设置 `GITHUB_TOKEN`；
5. 执行 `.un.ps1 --dry-run` 或正式扫描命令。

便携包无需 Maven，也无需安装 JDK。`SHA256SUMS.txt` 可用于核对包内文件完整性。

## 项目结构

    .
    ├─ common/
    │  ├─ config_files.json       # 配置文件名词池
    │  ├─ endpoint.json           # Endpoint / Base URL 词池
    │  └─ key_words.json          # 通用变量名词池
    ├─ providers/
    │  ├─ aliyun.json             # 阿里云 / DashScope / 通义千问
    │  ├─ deepseek.json           # DeepSeek
    │  └─ moonshot.json           # Moonshot / Kimi
    ├─ src/
    │  ├─ main/java/cn/leakscanner/
    │  │  ├─ Main.java            # 程序入口
    │  │  ├─ Options.java         # 参数解析
    │  │  ├─ github/              # GitHub API 客户端
    │  │  ├─ config/              # 词池加载
    │  │  ├─ scan/                # 查询、提取、评分和去重
    │  │  ├─ report/              # JSON / Markdown 报告
    │  │  └─ util/                # JSON 解析等工具
    │  └─ test/java/              # 自测
    ├─ build.ps1                 # 构建脚本
    ├─ run.ps1                   # PowerShell 启动脚本
    ├─ run.bat                   # CMD 启动脚本
    └─ test.ps1                  # 测试脚本

## 限制与误报

本项目是候选泄露发现工具，不是凭据有效性验证器，也不是完整的源代码安全分析平台。请注意：

* GitHub Code Search 结果受 GitHub 索引、权限和限流影响；
* 搜索结果可能不完整，程序会在终端提示 GitHub 标记的不完整结果；
* 正则提取无法覆盖所有编程语言、编码方式、字符串拼接和运行时解密逻辑；
* 示例 Key、测试 Key、文档片段和故意伪造的值可能产生误报；
* 真实凭据可能因为变量名不明显、拆分存储、加密存储、动态生成或未被索引而漏报；
* 风险评分只表示优先级，不表示凭据仍然有效；
* 工具不会自动撤销、轮换、通知仓库所有者或修复泄露文件；
* 工具默认只处理达到阈值的候选项，低分候选不会进入报告。

建议人工复核：变量上下文、提交历史、仓库归属、凭据所属账号、暴露范围、是否已经撤销，以及是否需要通知相关厂商。

## 故障排查

### 缺少 GitHub Token

错误示例：

    缺少 GitHub Token。请设置环境变量 GITHUB_TOKEN，或使用 --token。

解决：

    $env:GITHUB_TOKEN = '你的 GitHub Token'
    .\run.ps1 --dry-run
    .\run.ps1 --max-queries 3 --max-results-per-query 5

`--dry-run` 不需要 Token；正式扫描需要 Token。

### PowerShell 禁止执行脚本

如果本机策略阻止脚本运行，可以直接执行：

    powershell -ExecutionPolicy Bypass -File .\run.ps1 --dry-run

请根据组织安全策略决定是否使用该方式，不要为了运行工具长期降低系统执行策略。

### 查询速度较慢或出现 403/429

GitHub Search API 有独立限流。可以：

* 减少 `--max-queries`；
* 减少 `--max-results-per-query`；
* 增大 `--delay-ms`；
* 使用具有适当权限的 Token；
* 等待限流窗口恢复后再运行。

示例：

    .\run.ps1 --max-queries 10 --max-results-per-query 10 --delay-ms 10000

### 报告中没有发现项

可能原因包括：

* 所有候选项都低于 `--min-score`；
* GitHub 索引中没有匹配结果；
* 候选值被识别为占位符、示例值或测试值；
* 内容已经在增量状态中处理过；
* 查询或下载过程中出现错误。

可以先运行：

    .\run.ps1 --dry-run
    .\run.ps1 --min-score 40 --max-queries 5 --max-results-per-query 10

如果需要重新处理已扫描内容，请指定新的 `--state` 文件。

## 贡献指南

欢迎提交 Issue、Pull Request、词池扩展和测试用例。建议：

1. 提交前运行 `. est.ps1`；
2. 不要提交真实 Token、API Key、扫描报告或包含敏感信息的样例；
3. 新增厂商时，同时更新 `providers/`、Endpoint 词池和测试；
4. 修改报告格式时，同步更新 README 和自测断言；
5. 对限流、网络错误和解析失败保持可诊断性，不要静默吞掉错误；
6. 保持变更范围清晰，避免把构建产物和本地状态文件提交到源码仓库。

提交 Issue 时请提供：操作系统、JDK 版本、命令行参数、脱敏后的错误日志和是否使用便携版；不要上传 Token、报告或原始命中内容。

## 许可证

当前仓库尚未在项目根目录提供正式许可证文件。若要将项目作为开源项目发布，请在公开仓库前选择并添加适合的许可证，例如 MIT、Apache-2.0 或 GPL-3.0，并确保许可证与项目依赖、贡献策略和商业使用目标一致。

在添加许可证之前，其他人可能无法明确获得复制、修改和再分发本项目的法律授权。

## 免责声明

本项目按“现状”提供，不保证搜索结果完整、准确或及时，也不保证能够发现所有形式的敏感信息。使用者应自行确认目标范围、法律依据、组织授权和处置流程，并对使用本项目产生的结果负责。
