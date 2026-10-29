# 日志过滤语义

兼容 Android Studio Logcat 的核心字段查询风格，不包含 IDE 项目模型、源码跳转与多窗格比较。正则使用浏览器 JavaScript RegExp，不保证兼容 Java 正则的全部特性。

| 条件 | 含义 |
| --- | --- |
| `timeout`、`"connection failed"` | 搜索消息内容，默认区分大小写，可切换忽略大小写 |
| `package:com.example` | 包名包含指定文本 |
| `process:remote` | 已知进程名包含 remote；未知进程不会猜测为主进程 |
| `tag:Network`、`message:timeout`、`line:timeout` | 分别匹配标签、消息、原始行 |
| `pid:123`、`tid:456`、`uid:10001` | 数字精确相等 |
| `level:WARN` | WARN 及以上，支持 VERBOSE、DEBUG、INFO、WARN、ERROR、ASSERT；允许常用缩写与 FATAL 别名 |
| `-tag:Noise` | 排除标签包含 Noise 的日志 |
| `message~:"timeout|failed"` | 对消息使用正则；值包含空格、括号或逻辑运算符时必须加引号 |
| `-tag~:"Noise.*"` | 排除正则命中的标签 |
| `age:5m` | 最近五分钟，支持整数 s/m/h/d，按手机时间计算 |
| `is:crash`、`is:stacktrace` | 匹配可识别的崩溃组或 Java 堆栈组 |
| `package:mine` | 当前应用列表选中的包名集合；未选择时提示错误 |
| `name:"网络错误"` | 为历史或收藏提供显示名称，不参与匹配 |

普通字段为包含匹配，数字字段为精确匹配。多选应用列表和等级下拉框是查询表达式之外的额外 AND 条件：选择应用后，其他应用日志即使满足查询也不会显示。

## 组合

显式 `&` 优先于 `|`；括号改变优先级。空白连接的条件采用较低优先级的隐式逻辑，相同字段的多个正向条件 OR，其他条件 AND。

```text
package:com.example.app level:WARN -tag:Noise
(tag:Network | message~:"timeout|failed") & package:mine
tag:Network tag:Database package:com.example
name:"异常" package:mine is:crash
```

第三个示例等价于 `(tag:Network | tag:Database) & package:com.example`。多个无字段的普通词分别要求命中消息；要匹配连续短语请使用引号。

错误的字段、数字、等级、时间单位、括号、引号或正则会显示错误，不将错误输入当作成功过滤。高开销正则在工作线程执行，超过约 1.5 秒会被终止，修改条件可恢复。

## 堆栈、上下文与缓存

相邻同 PID、TID、标签且时间间隔不超过两秒的异常及堆栈行按启发式归组。命中组内任意一行时显示完整组；这不等于能精准识别所有自定义日志格式。Java 异常和常见原生崩溃标记均参与识别。

“查看上下文”显示所选记录周边的原始缓存日志，临时忽略过滤条件。“暂停显示”不停止采集；恢复后显示最新缓存。“清空视图”只设置本页游标，不清空手机系统 Logcat，也不影响其他客户端。有过滤条件时，命中日志另有 5 万条 / 32 MB 的保留预算，避免被无关日志挤出；达到上限后按组淘汰旧结果。切换条件会释放原先保留结果并从当前原始缓存重新筛选。已离开原始缓存的结果仍可显示和导出，但无法保证查看周边上下文。取消跟随时会尽量固定阅读位置；当前日志被淘汰后无法继续定位。

“导出当前过滤结果”包含完整的命中组；“全部缓存”包含浏览器目前保留的原始日志，不受筛选或视图清空影响。导出弹窗会固定生成时的文本快照，可下载或复制。

参考：https://developer.android.com/studio/debug/logcat
