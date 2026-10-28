package dev.weblogcat.probe

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class AppCatalog(context: Context) {
    private val pm = context.packageManager
    @Volatile private var packages: Map<Int, List<String>> = emptyMap()
    @Volatile private var installed: Set<String> = emptySet()
    @Volatile private var apps = JSONArray()
    private val probes = ConcurrentHashMap<String, Long>()
    private val processes = ConcurrentHashMap<String, String>()
    init { refresh(); warmProcesses() }
    private fun warmProcesses() {
        // 读取保留的进程生命周期事件，补充服务启动前已存在的多进程名称。
        var history: Process? = null
        try {
            history = ProcessBuilder("logcat", "-b", "events", "-d", "-v", "epoch", "-v", "uid", "-s", "am_proc_start:I", "am_proc_died:I").start()
            val current = history
            Handler(Looper.getMainLooper()).postDelayed({ current.destroy() }, 3000)
            history.inputStream.bufferedReader().useLines { lines -> lines.forEach { line -> LogEntry.parse(line)?.let(::resolve) } }
        } catch (_: IOException) { } finally { history?.destroy() }
    }
    @Synchronized fun refresh() {
        val mapping = HashMap<Int, MutableList<String>>(); val names = HashSet<String>(); val list = JSONArray()
        val infos = pm.getInstalledApplications(0).sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
        for (info in infos) {
            names.add(info.packageName); mapping.getOrPut(info.uid) { ArrayList() }.add(info.packageName)
            val app = JSONObject().put("package", info.packageName).put("name", pm.getApplicationLabel(info).toString()).put("uid", info.uid).put("system", info.flags and ApplicationInfo.FLAG_SYSTEM != 0)
            list.put(app)
        }
        packages = mapping; installed = names; apps = list
    }
    fun json(): JSONArray = apps
    fun resolve(entry: LogEntry) {
        if (entry.uid < 0) entry.uid = android.os.Process.getUidForName(entry.user)
        // 进程事件可补充多进程名称；共享 UID 不能直接等同于唯一包名。
        if (entry.tag == "am_proc_start") {
            val fields = entry.message.removePrefix("[").split(",", limit = 6)
            if (fields.size >= 4) processes[fields[1].trim() + ":" + fields[2].trim()] = fields[3].trim()
        }
        if (entry.tag == "am_proc_died") {
            val fields = entry.message.removePrefix("[").split(",", limit = 4)
            if (fields.size >= 2) processes.keys.removeIf { it.startsWith(fields[1].trim() + ":") }
        }
        val key = "${entry.pid}:${entry.uid}"
        var process = processes[key].orEmpty()
        if (process.isEmpty() && System.currentTimeMillis() - (probes[key] ?: 0L) > 15000) {
            probes[key] = System.currentTimeMillis()
            try {
                FileInputStream("/proc/${entry.pid}/cmdline").use { input ->
                    val b = ByteArray(256); val n = input.read(b); var end = 0
                    while (end < n && b[end] != 0.toByte()) end++
                    process = String(b, 0, end, Charsets.UTF_8)
                    if (process.isNotEmpty()) processes[key] = process
                }
            } catch (_: IOException) { }
        }
        entry.process = process
        val candidates = packages[entry.uid].orEmpty()
        if (candidates.size == 1 && entry.uid % 100000 in 10000 until 99000) entry.packageName = candidates[0]
        else { val base = process.substringBefore(':'); if (base in candidates) entry.packageName = base }
        entry.bytes += 2 * (entry.packageName.length + entry.process.length)
    }
    fun icon(packageName: String): ByteArray {
        if (packageName !in installed) throw FileNotFoundException()
        val icon = pm.getApplicationIcon(packageName); val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 64, 64); icon.draw(Canvas(bitmap))
        val stream = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); bitmap.recycle(); return stream.toByteArray()
    }
}
