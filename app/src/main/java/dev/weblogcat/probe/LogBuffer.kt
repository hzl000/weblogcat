package dev.weblogcat.probe

import java.util.ArrayDeque

/** 同时限制条数和估算存储字节数，淘汰时保持游标单调递增。 */
class LogBuffer @JvmOverloads constructor(private val maxCount: Int = 50000, private val maxBytes: Long = 32L * 1024 * 1024) {
    private val entries = ArrayDeque<LogEntry>()
    private var next = 1L
    private var bytes = 0L
    private var dropped = 0L
    @Synchronized fun add(entry: LogEntry) {
        entry.id = next++; entries.addLast(entry); bytes += entry.bytes
        while (entries.size > maxCount || bytes > maxBytes) { bytes -= entries.removeFirst().bytes; dropped++ }
    }
    @Synchronized fun after(cursor: Long, limit: Int): List<LogEntry> {
        val result = ArrayList<LogEntry>()
        for (entry in entries) if (entry.id > cursor) { result.add(entry); if (result.size >= limit) break }
        return result
    }
    @Synchronized fun latest() = next - 1
    @Synchronized fun oldest() = if (entries.isEmpty()) next else entries.first.id
    @Synchronized fun size() = entries.size
    @Synchronized fun bytes() = bytes
    @Synchronized fun dropped() = dropped
}
