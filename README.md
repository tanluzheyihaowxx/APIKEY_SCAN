# ApiKey_Scan

`ApiKey_Scan` 是一个基于 Java 的 GitHub Code Search API Key 泄露检测工具，用于在**已获得明确授权**的代码仓库范围内发现疑似 API Key、Token、Secret 和相关配置，并生成扫描报告。

> 本项目仅适用于安全测试、内部审计、应急响应和其他合法授权场景。请勿扫描无权访问的仓库、组织或个人数据。

## 项目功能

* 使用 GitHub Code Search 检索可能包含敏感配置的代码文件
* 内置常见 API Key、Token、Secret、Base URL 和配置文件关键词
* 支持阿里云 DashScope/百炼、DeepSeek、Moonshot/Kimi 等规则
* 对候选内容进行上下文分析、规则匹配和风险评分
* 支持增量扫描状态，减少重复处理
* 支持自定义查询、结果数量、评分阈值和请求间隔
* 输出 JSON 和 Markdown 扫描报告
* 支持 `--dry-run`，用于预览查询而不访问 GitHub

## 运行环境

* Windows 10/11 或其他支持 PowerShell 的系统
* Java 17 或更高版本
* `java`、`javac` 和 `jar` 已加入系统 `PATH`
* 具有适当 Code Search 权限的 GitHub Token

检查 Java 是否可用：

    java -version
    javac -version
    jar --version

## 快速开始

### 1. 配置 GitHub Token

推荐使用环境变量，不要把真实 Token 写入脚本、README 或配置文件。

PowerShell：

    $env:GITHUB_TOKEN = "你的 GitHub Token"

CMD：

    set GITHUB_TOKEN=你的 GitHub Token

程序也支持 `GH_TOKEN` 环境变量，或使用命令行参数 `--token`。不建议把 Token 直接写在命令行历史中。

### 2. 启动程序

推荐使用 PowerShell 启动脚本：

    .\run.ps1

Windows CMD：

    run.bat

也可以直接运行 JAR：

    java -jar .\build\ApiKey_Scan.jar --root .

如果 JAR 不存在，`run.ps1` 和 `run.bat` 会自动调用 `build.ps1` 构建程序。

## 常用命令

显示帮助：

    .\run.ps1 --help

只生成查询，不访问 GitHub：

    .\run.ps1 --dry-run

指定报告目录：

    .\run.ps1 --output .\reports

设置最低风险评分：

    .\run.ps1 --min-score 80

限制最大查询数量：

    .\run.ps1 --max-queries 20

追加自定义查询：

    .\run.ps1 --query '"DEEPSEEK_API_KEY"'

可以重复使用 `--query`：

    .\run.ps1 `
      --query '"DEEPSEEK_API_KEY"' `
      --query '"DASHSCOPE_API_KEY"' `
      --query '"MOONSHOT_API_KEY"'

包含 fork 仓库：

    .\run.ps1 --include-forks

## 命令行参数

| 参数  | 说明  | 默认值 |
| --- | --- | --- |
| `--root <dir>` | 词池、配置和运行数据根目录 | 当前目录 |
| `--output <dir>` | JSON/Markdown 报告目录 | `<root>/reports` |
| `--state <file>` | 增量扫描状态文件 | `<root>/.ApiKey_Scan/state.json` |
| `--token <token>` | GitHub Token | `GITHUB_TOKEN` 或 `GH_TOKEN` |
| `--min-score <0-100>` | 报告最低评分阈值 | `70` |
| `--max-queries <n>` | 单次最大查询数量 | `60` |
| `--max-results-per-query <n>` | 每条查询最多处理的结果数 | `30` |
| `--per-page <1-100>` | GitHub 每页结果数量 | `30` |
| `--delay-ms <ms>` | 搜索请求之间的等待时间 | `6500` |
| `--query <query>` | 追加自定义 GitHub Code Search 查询，可重复 | 无   |
| `--include-forks` | 不过滤 fork 仓库 | 关闭  |
| `--dry-run` | 只生成查询，不访问 GitHub | 关闭  |
| `--help` / `-h` | 显示帮助 | -   |

## 项目目录

    ApiKey_Scan/
    ├─ build/
    │  └─ ApiKey_Scan.jar
    ├─ common/
    │  ├─ config_files.json
    │  ├─ endpoint.json
    │  └─ key_words.json
    ├─ providers/
    │  ├─ aliyun.json
    │  ├─ deepseek.json
    │  └─ moonshot.json
    ├─ src/
    │  ├─ main/java/cn/apikeyscan/
    │  └─ test/java/cn/apikeyscan/
    ├─ build.ps1
    ├─ run.bat
    ├─ run.ps1
    ├─ test.ps1
    └─ readme.md

## 配置文件

### `common/key_words.json`

关键词和变量名规则，例如：

* `API_KEY`
* `API_SECRET`
* `API_TOKEN`
* `DEEPSEEK_API_KEY`
* `DASHSCOPE_API_KEY`
* `MOONSHOT_API_KEY`

### `common/endpoint.json`

服务端点规则，例如：

* `api.deepseek.com`
* `dashscope.aliyuncs.com`
* `api.moonshot.cn`

### `common/config_files.json`

常见配置文件名，例如：

* `.env`
* `application.yml`
* `application.properties`
* `docker-compose.yml`
* `config.json`

### `providers/*.json`

服务商专属关键词和环境变量规则。可以根据授权测试范围增加新的服务商配置。

## 输出文件

默认输出到 `reports/`：

    reports/
    ├─ ApiKey_Scan-report-YYYYMMDD-HHmmss.json
    └─ ApiKey_Scan-report-YYYYMMDD-HHmmss.md

增量状态默认保存在：

    .ApiKey_Scan/state.json

### 报告中的敏感信息

JSON 报告使用脱敏值和 SHA-256 指纹进行记录。

当前 Markdown 报告实现会把高风险命中的候选值以明文写入报告，便于在**受控的内部环境**中核验。因此：

* 不要把 `reports/` 提交到 Git
* 不要把扫描报告上传到公开网盘或聊天群
* 不要把报告发送给无关人员
* 不要在报告中出现真实凭据后继续转发或复制
* 如果发现真实泄露，应立即撤销或轮换对应 Token

如果不需要明文核验，应先修改 `ReportWriter.java`，让 Markdown 使用 `maskedValue()`，再进行发布或共享。

## 构建

在项目根目录执行：

    .\build.ps1

构建产物：

    build/ApiKey_Scan.jar

构建时会清理并重新生成主程序编译目录。发布前建议确认 JAR 中只包含 `cn/apikeyscan` 包。

## 测试

运行自测：

    .\test.ps1

测试内容包括：

* 词池和服务商配置加载
* 查询生成
* 三家服务商的候选提取
* 风险评分
* 占位符降权
* 报告格式生成

测试文件中的 `sk-...` 字符串是伪造测试夹具，不是生产凭据。公开发布前仍建议将测试值替换为更明显的占位符或改为运行时随机生成。

## 安全使用建议

1. 使用最小权限、短有效期的 GitHub Token。
2. 不要将 Token 写入源代码、配置文件、命令历史或 README。
3. 扫描前确认目标仓库和组织属于授权范围。
4. 报告目录应使用访问控制，必要时加密保存。
5. 发现真实 API Key 后，优先撤销、轮换并通知凭据所有者。
6. 不要为了验证密钥而调用第三方 API，除非获得明确授权。
7. 发布前检查 ZIP、Git 历史、备份和报告目录中是否残留敏感信息。

## 隐私与数据处理

程序会访问 GitHub Code Search，并可能下载搜索结果中的代码内容用于本地规则分析。请根据组织安全政策处理：

* GitHub Token
* 仓库地址和路径
* 代码上下文
* 扫描报告
* 增量状态文件

## 免责声明

ApiKey_Scan 仅提供辅助检测能力，不保证发现所有秘密，也不保证每个命中都是真实凭据。使用者应对扫描范围、授权、结果判断、数据保护和后续处置承担全部责任。

使用本工具即表示你理解并遵守适用的法律法规、GitHub 服务条款以及所在组织的安全策略。
