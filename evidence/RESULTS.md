# READ_LOGS 验证结果

验证日期：2026-10-27（Asia/Hong_Kong）。

结论：在本次 Pixel 10 / Android 17 上，READ_LOGS 方案可以实现手机端独立采集跨 UID 日志，局域网浏览器实时查看，拔掉 USB 后继续工作。前提是 ADB 授权后在应用前台通过系统“一次性允许”确认，并维持该采集进程。后台重建采集进程受到系统限制。

## 环境

- 手机：Pixel 10，Android 17，API 37。
- 系统构建：`google/frankel/frankel:17/CP2A.260805.005/15828068:user/release-keys`。
- APK：`dev.weblogcat.probe`，UID 10803；compileSdk/targetSdk 36，minSdk 26。
- shell 日志刺激：UID 2000。采集 logcat 由 APK 自身启动，未借用 shell 身份。
- 电脑：192.168.203.42；最终手机地址：192.168.203.150；端口：8765。
- 初始手机在 192.168.50.215，电脑无法访问。用户调整 Wi-Fi 后，直连通过。
- 设备日志时钟与电脑时钟不一致；等待时长和拔线先后以电脑记录时间为准，不比较两者墙上时间。

## 实测

| 项目 | 结果与证据 |
| --- | --- |
| 未授予 READ_LOGS | 通过：采集进程存活，但读不到 shell 标记。`baseline.json` |
| 仅 pm grant | 权限变为 true，但不意味着已有全日志访问权；首次锁屏尝试未读到标记。`granted.json` |
| 前台一次性确认 | 系统明确弹出允许访问所有设备日志的确认框。确认后读到 shell 标记。冷启动再次观察到确认框。`foreground-approved.json` |
| 后台持续超过 60 秒 | 通过：前台确认后按 Home，超过 60 秒后，原服务读取新标记。最终版本通过 Wi-Fi 直连复核，服务 started 值一致。`background-existing.json` |
| 后台重建 logcat | 观察到限制：READ_LOGS=true、进程存活，但新 shell 标记不可见。此项在首版验证 APK 上测试，最终版本仅修正旧读取线程退出时的错误提示，权限路径未改变。`background-restart.json` |
| 局域网直连 | 通过：移除 ADB 转发后，通过手机 Wi-Fi 地址读取新标记。`lan-direct.json` |
| 浏览器显示和筛选 | 通过：浏览器显示 UID、权限和进程状态，关键词筛选只显示测试 tag。`lan-unplugged.png` |
| 真实拔掉 USB | 通过：用户确认拔线，ADB 列表不再包含目标手机。确认断线后约 53 秒，Wi-Fi 收到事先安排的 90 秒延迟标记；服务 started 值仍一致。`usb-unplugged.json` |
| 无访问 token | 通过：HTTP 返回 403。 |
| 停止服务 | 通过：用户点击停止后，Wi-Fi TCP 8765 明确拒绝连接。`stop.json` |

延迟 shell 进程只负责生成一条测试日志，不承担日志读取或 HTTP 服务。拔线验证期间由应用 UID 10803 的服务继续读取和响应。

## 交付与边界

工程源码、可重复执行的标记检查脚本、debug APK 均已保存。当前手机服务已停止，临时 ADB 转发已移除。READ_LOGS 的 ADB 授权保留。

这证明当前机型与系统上的可行性，不证明其他厂商、其他 Android 版本、锁屏长时间运行、低电量/Doze、系统杀进程或重启后自动恢复。后台重建失败应提示用户返回应用前台重新允许访问；不能把永久 READ_LOGS 授权理解为永久全设备日志会话。

官方行为参考：[AOSP 日志访问说明](https://source.android.com/docs/core/tests/debug/understanding-logging)。

APK SHA-256：`78451c7036124ae9973267dee6e6f866bded412ec934c2c57dd12d998be81d3c`。
