package dev.weblogcat.probe

import java.util.regex.Pattern

/** 日志字段在采集时固定，避免进程重启后历史日志归属改变。 */
class LogEntry {
    @JvmField var id = 0L
    @JvmField var time = 0L
    @JvmField var uid = 0
    @JvmField var pid = 0
    @JvmField var tid = 0
    @JvmField var user = ""
    @JvmField var level = ""
    @JvmField var tag = ""
    @JvmField var message = ""
    @JvmField var process = ""
    @JvmField var packageName = ""
    @JvmField var raw = ""
    @JvmField var bytes = 0

    companion object {
        private val format = Pattern.compile("^\\s*(\\d+\\.\\d+)\\s+(\\S+)\\s+(\\d+)\\s+(\\d+)\\s+([VDIWEFAS])\\s+(.*?):\\s?(.*)$")
        @JvmStatic fun parse(line: String): LogEntry? {
            val m = format.matcher(line)
            if (!m.matches()) return null
            val e = LogEntry()
            e.time = (m.group(1)!!.toDouble() * 1000).toLong()
            val user = m.group(2)!!
            e.user = user
            e.uid = user.toIntOrNull() ?: when {
                user == "shell" -> 2000
                user == "root" -> 0
                user.matches(Regex("u\\d+_a\\d+")) -> {
                    val parts = user.substring(1).split("_a")
                    parts[0].toInt() * 100000 + 10000 + parts[1].toInt()
                }
                else -> -1
            }
            e.pid = m.group(3)!!.toInt(); e.tid = m.group(4)!!.toInt()
            e.level = if (m.group(5) == "A") "F" else m.group(5)!!
            e.tag = m.group(6)!!.trim(); e.message = m.group(7)!!; e.raw = line
            e.bytes = 2 * (line.length + e.message.length + e.tag.length + user.length) + 256
            return e
        }
    }
}
