ApiKey_Scan
ApiKey_Scan 是一个基于 Java 的 GitHub Code Search API Key 泄露检测工具，用于在已获授权的代码仓库范围内发现疑似云服务、AI 平台和其他 API 凭据，并输出 JSON 与 Markdown 报告。

仅限安全测试、内部审计、应急响应和其他获得明确授权的场景。请勿使用本工具扫描无权访问的仓库、组织或个人数据。

功能
基于 GitHub Code Search 检索候选代码文件
内置常见 API Key、Token、Secret 关键词池
对候选内容进行规则匹配、上下文提取和风险评分
支持增量状态，避免重复处理相同对象和内容
输出 JSON、Markdown 两种报告格式
支持自定义查询、阈值、查询数量、分页和请求间隔
支持 --dry-run，在不访问 GitHub 的情况下预览查询
运行环境
Windows 10/11 或其他支持 PowerShell 的系统
Java 17 或更高版本，且 java、javac、jar 已加入 PATH
一个具有适当 Code Search 权限的 GitHub Token
快速开始
1. 设置 GitHub Token
PowerShell：

$env:GITHUB_TOKEN = "ghp_xxxxxxxxxxxxxxxxxxxx"
CMD：

set GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxx
也可以在命令行中使用 --token，但不建议把 Token 直接写入历史命令或脚本。

2. 启动扫描
推荐使用启动脚本，脚本会在缺少 JAR 时自动构建：

.\run.ps1
或：

run.bat
直接运行 JAR：

java -jar .\build\ApiKey_Scan.jar --root .
首次运行前也可以手动构建：

.\build.ps1
常用命令
预览将要执行的查询，不访问 GitHub：

.\run.ps1 --dry-run
指定输出目录：

.\run.ps1 --output .\reports
提高或降低报告阈值：

.\run.ps1 --min-score 80
追加自定义查询：

.\run.ps1 --query '"sk-" extension:env'
允许包含 fork 仓库：

.\run.ps1 --include-forks
命令行参数
参数	说明	默认值
--root <dir>	词池和运行数据根目录	当前目录
--output <dir>	报告输出目录	<root>/reports
--state <file>	增量状态文件	<root>/.ApiKey_Scan/state.json
--token <token>	GitHub Token	优先读取 GITHUB_TOKEN 或 GH_TOKEN
--min-score <0-100>	报告评分阈值	70
--max-queries <n>	单次最大查询数	60
--max-results-per-query <n>	每条查询最多处理结果数	30
--per-page <1-100>	GitHub 每页结果数	30
--delay-ms <ms>	搜索请求间隔	6500
--query <query>	追加自定义查询，可重复	无
--include-forks	不过滤 fork 仓库	关闭
--dry-run	只生成查询，不访问 GitHub	关闭
--help / -h	显示帮助	-
输出文件
默认输出到 reports/：

ApiKey_Scan-report-*.json：结构化报告，便于二次处理
ApiKey_Scan-report-*.md：可读的 Markdown 报告
.ApiKey_Scan/state.json：增量扫描状态，请妥善保存
报告中的 Token、Secret 等敏感字段会按程序规则脱敏；但报告本身仍可能包含仓库地址、路径和代码上下文，请按敏感数据管理。

目录结构
ApiKey_Scan/
├─ build/ApiKey_Scan.jar
├─ run.bat
├─ run.ps1
├─ build.ps1
├─ test.ps1
├─ common/
│  ├─ config_files.json
│  ├─ endpoint.json
│  └─ key_words.json
├─ providers/
│  ├─ aliyun.json
│  ├─ deepseek.json
│  └─ moonshot.json
├─ src/
│  ├─ main/java/
│  └─ test/java/
└─ readme.md
构建与测试
构建发布 JAR：

.\build.ps1
运行自测：

.\test.ps1
构建产物位于：

build/ApiKey_Scan.jar
配置说明
common/key_words.json：关键词、正则和评分规则
common/config_files.json：配置文件识别规则
common/endpoint.json：端点和 URL 相关规则
providers/*.json：不同服务商的补充规则
修改配置后无需修改 Java 源码，重新运行即可使用新的规则。

安全建议
使用最小权限 Token，并设置合理的有效期。
不要把 Token 写入 README、配置文件、批处理脚本或 Git 仓库。
报告应存放在受控目录，必要时加密传输和归档。
发现真实泄露后，应立即撤销或轮换凭据，并通知相关服务商和仓库所有者。
遵守 GitHub 服务条款、组织安全策略和适用法律法规。
免责声明
ApiKey_Scan 仅提供辅助检测能力，不保证发现全部秘密，也不保证每个命中都是真实凭据。使用者应对扫描范围、授权、结果判断和后续处置承担全部责任。
