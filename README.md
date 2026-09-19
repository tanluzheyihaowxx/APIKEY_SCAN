# ApiKey_Scan 项目泄露检查报告

* **检查日期**：2026-09-19
* **检查对象**：`ApiKey_Scan-20260919.zip` 及其解压后的项目文件
* **检查范围**：Java 源码、测试代码、JSON 配置、PowerShell/CMD 脚本、README、JAR 包内容
* **检查方式**：敏感凭据模式检索、配置审阅、JAR 条目检查、构建产物检查

## 结论

在本次检查的项目文件中，**没有发现可确认的真实生产凭据**，包括 GitHub Token、AWS Access Key、Google API Key、Slack Token、私钥等。

发现的是测试用的伪造 API Key 样例，以及一个需要重点修复的报告输出设计：Markdown 报告会把命中的候选 API Key 以明文写入文件。如果扫描到真实凭据，生成的 Markdown 报告本身就会成为一份新的敏感信息泄露副本。

## 发现项

### [高风险] Markdown 报告明文输出候选 API Key

**位置**：

    src/main/java/cn/apikeyscan/report/ReportWriter.java

`markdown()` 方法使用 `f.rawValue()` 生成如下格式：

    baseurl:<Base URL>;apikey:<API Key>

这与 JSON 报告使用 `masked_value` 的做法不一致。当前 README 也声明报告中的敏感字段会脱敏，但实际 Markdown 报告并不会脱敏。

**影响**：

* 一旦扫描到真实 API Key，`reports/ApiKey_Scan-report-*.md` 会保存完整凭据。
* 报告可能被提交到 Git、同步到云盘、发送到聊天工具或写入备份，从而扩大泄露范围。
* 任何能够读取报告的人都可能直接获得可用凭据。

**建议**：

1. Markdown 默认使用 `f.maskedValue()`，不要使用 `f.rawValue()`。
2. 如确实需要内部取证，增加显式的本地开关，并默认关闭。
3. 更新 `SelfTest.java`，验证 Markdown 不包含原始 Key。
4. 修改 README，明确所有报告默认只保存脱敏值。
5. 对现有 `reports/` 目录做一次凭据检查；如果已经产生过真实报告，应立即轮换其中的凭据。

### [低风险] 测试代码包含伪造 API Key 样例

**位置**：

    src/test/java/cn/apikeyscan/SelfTest.java

其中包含 `sk-` 开头的固定字符串，用于测试 DeepSeek、百炼和 Moonshot 的识别与脱敏逻辑。这些值呈现出明显的重复十六进制模式，属于测试夹具，未发现其为真实可用凭据。

**建议**：

* 将测试值替换为更明显的占位符，或在运行时生成随机测试值。
* 如果项目要提交到公开仓库，避免使用看起来像真实凭据的固定前缀和长度。
* 在 Secret Scanning 规则中将测试目录作为已知测试样例处理，但不要因此忽略其他目录的真实命中。

### [低风险] JAR 内存在旧包名的遗留编译类

原压缩包中的 `build/ApiKey_Scan.jar` 同时包含：

    cn/apikeyscan/...
    cn/leakscanner/...

这不是凭据泄露，但说明 JAR 曾使用未清理的旧编译目录打包，可能导致旧代码残留、版本混淆或审计困难。发布前应清空 `build/classes` 后重新构建。

## 已检查且未发现真实值的模式

在项目文本文件和 JAR 内容中未发现以下类型的真实凭据：

* GitHub `ghp_...` 或 `github_pat_...` Token
* AWS `AKIA...` Access Key
* Google `AIza...` API Key
* Slack `xox...` Token
* PEM/RSA/OpenSSH/EC/DSA 私钥块
* 明显的生产密码、Bearer Token 或固定访问密钥

README 中出现的 `ghp_xxxxxxxxxxxxxxxxxxxx` 是示例占位符；测试代码中的 `sk-...` 是固定测试夹具，不应当被当作已验证的生产凭据。

## 建议的修复优先级

1. **立即**：停止把 `rawValue` 写入 Markdown 报告，重新构建 JAR。
2. **立即**：检查并清理已有 `reports/`、压缩包和备份中的真实扫描结果。
3. **随后**：把 SelfTest 中的固定 Key 改为明显占位符或运行时随机值。
4. **发布前**：清空旧编译目录，重新生成 JAR，并确认 JAR 中只存在 `cn/apikeyscan` 包。
5. **发布前**：对最终 ZIP 运行一次 Secret Scanning，并确认没有 `.env`、Token、私钥或历史报告文件。

## 限制

本报告只覆盖当前压缩包和解压后的文件内容，不能证明项目历史提交、Git reflog、云盘历史版本、外部备份或曾经生成的报告中不存在凭据。如果该项目曾经使用过真实 Token，仍应检查 Git 历史和备份，并在无法确认安全时轮换相关凭据。
