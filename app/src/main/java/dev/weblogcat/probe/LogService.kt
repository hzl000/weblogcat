package dev.weblogcat.probe

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class LogService : Service() {
    private val buffer = LogBuffer()
    private val clients = ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS, ArrayBlockingQueue(24))
    @Volatile private var reader: Process? = null
    @Volatile private var verificationReader: Process? = null
    @Volatile var verifying = false
    @Volatile private var accessVerified = false
    private var verificationGeneration = 0
    private var desiredPort = 0
    @Volatile private var server: ServerSocket? = null
    @Volatile private var running = false
    @Volatile var error = ""
    @Volatile var port = 0
    private var started = 0L
    @Volatile private var lastLogAt = 0L
    @Volatile private var lastForeignAt = 0L
    @Volatile private var catalog: AppCatalog? = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("logs", "日志采集", NotificationManager.IMPORTANCE_LOW))
        // 即使没有权限也先满足前台服务启动要求，再立即停止。
        if (!hasReadLogs(this)) { notifyRunning(); stopSelf(); return }
        active = this; lastFailure = ""; started = System.currentTimeMillis(); running = true
        notifyRunning(); Thread({ catalog = AppCatalog(this) }, "app-catalog").start()
    }
    private fun notifyRunning() {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        startForeground(1, Notification.Builder(this, "logs").setContentTitle(if (accessVerified) "WebLogcat 正在运行" else "WebLogcat 等待日志访问").setContentText("端口 ${if (port == 0) configuredPort(this) else port} · 点击管理").setSmallIcon(android.R.drawable.stat_notify_sync).setContentIntent(open).build())
    }
    override fun onStartCommand(intent: Intent?, flags: Int, id: Int): Int {
        if (!hasReadLogs(this)) { stopSelf(); return START_NOT_STICKY }
        val requested = intent?.getIntExtra("port", configuredPort(this)) ?: configuredPort(this)
        Thread({ handleCommand(requested, intent?.action == "restart") }, "service-command").start()
        return START_NOT_STICKY
    }
    @Synchronized private fun handleCommand(requested: Int, restart: Boolean) {
        if (!running) return
        if (!hasReadLogs(this)) { stopSelf(); return }
        if (requested !in 1024..65535) { error = "端口必须为 1024–65535"; return }
        desiredPort = requested
        if (accessVerified && !restart) { bind(requested); return }
        if (verifying && !restart) return
        beginVerification()
    }
    @Synchronized private fun beginVerification() {
        val generation = ++verificationGeneration
        verifying = true; accessVerified = false; error = ""; lastFailure = ""
        verificationReader?.destroy(); try { server?.close() } catch (_: IOException) { }
        server = null; port = 0; restartReader(); notifyRunning()
        if (reader == null) { failVerification(generation, "无法启动日志采集，请重试"); return }
        try {
            // 仅凭 main 缓冲区的其他 UID 日志确认系统允许访问。
            val probe = ProcessBuilder("logcat", "-b", "main", "-v", "epoch", "-v", "uid", "-T", "1", "*:V").start()
            verificationReader = probe
            Thread({
                try { probe.inputStream.bufferedReader().use { input ->
                    while (true) { val line = input.readLine() ?: break; val entry = LogEntry.parse(line)
                        if (entry != null && entry.uid >= 0 && entry.uid != android.os.Process.myUid()) { finishVerification(generation, probe); break }
                    }
                } } catch (_: IOException) { }
            }, "log-access-verification").start()
            Handler(Looper.getMainLooper()).postDelayed({ failVerification(generation, "未确认全设备日志访问。请返回应用重试，并在系统弹窗中允许。") }, 60000)
        } catch (e: IOException) { failVerification(generation, "无法检查日志访问：${e.message}") }
    }
    @Synchronized private fun finishVerification(generation: Int, probe: Process) {
        if (!running || generation != verificationGeneration || !verifying) return
        probe.destroy(); verificationReader = null
        if (!hasReadLogs(this) || reader?.isAlive != true) { failVerification(generation, "日志权限或采集进程不可用，请重试。"); return }
        verifying = false; accessVerified = true
        if (!bind(desiredPort)) { lastFailure = error; accessVerified = false; stopSelf(); return }
        notifyRunning()
    }
    @Synchronized private fun failVerification(generation: Int, message: String) {
        if (!running || generation != verificationGeneration || !verifying) return
        verifying = false; accessVerified = false; lastFailure = message; error = message
        verificationReader?.destroy(); verificationReader = null; stopSelf()
    }
    @Synchronized private fun bind(requested: Int): Boolean {
        if (requested !in 1024..65535) { error = "端口必须为 1024–65535"; return false }
        if (server?.isClosed == false && port == requested) return true
        return try {
            val replacement = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress("0.0.0.0", requested), 8) }
            val old = server; server = replacement; port = requested; error = ""
            getSharedPreferences("server", 0).edit().putInt("port", port).apply(); old?.close()
            Thread({ serve(replacement) }, "http-listener").start(); notifyRunning(); true
        } catch (e: IOException) { error = "端口启动失败：${e.message}"; false }
    }
    @Synchronized private fun restartReader() {
        error = ""; reader?.destroy()
        try {
            // UID 和设备时间戳由日志源提供，筛选不会重新启动采集进程。
            val current = ProcessBuilder("logcat", "-b", "main", "-b", "system", "-b", "crash", "-b", "events", "-v", "epoch", "-v", "uid", "-T", "1", "*:V").start(); reader = current
            Thread({
                try { current.inputStream.bufferedReader().use { input ->
                    while (reader === current && running) { val line = input.readLine() ?: break; val entry = LogEntry.parse(line) ?: continue
                        catalog?.resolve(entry); buffer.add(entry); lastLogAt = System.currentTimeMillis()
                        if (entry.uid >= 0 && entry.uid != android.os.Process.myUid()) lastForeignAt = lastLogAt
                    }
                } } catch (e: Exception) { if (running && reader === current) error = "采集退出：${e.message}" }
            }, "logcat-reader").start()
            Thread({ try { current.errorStream.bufferedReader().use { input -> while (reader === current) { val line = input.readLine() ?: break; error = line } } } catch (_: IOException) { } }, "logcat-errors").start()
        } catch (e: IOException) { error = "无法启动采集：${e.message}" }
    }
    private fun serve(socket: ServerSocket) {
        while (running && !socket.isClosed) try {
            val client = socket.accept(); try { clients.execute { respond(client) } } catch (_: RejectedExecutionException) { client.close() }
        } catch (e: IOException) { if (running && !socket.isClosed) error = e.message.orEmpty() }
    }
    private fun readLine(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        while (true) { val c = input.read(); if (c == -1) return null; if (c == '\n'.code) return out.toString("US-ASCII").replace("\r", ""); if (out.size() >= 8192) throw IOException("请求过长"); out.write(c) }
    }
    private fun respond(socket: Socket) {
        try { socket.use { s ->
            if (!hasReadLogs(this)) { stopSelf(); return }
            s.soTimeout = 3000; val request = readLine(s.getInputStream()) ?: return
            val parts = request.split(" "); if (parts.size != 3) return
            var headerBytes = 0
            while (true) { val header = readLine(s.getInputStream()) ?: break; if (header.isEmpty()) break; headerBytes += header.length; if (headerBytes > 16384) return }
            val uri = URI.create(parts[1]); val query = HashMap<String, String>()
            uri.rawQuery?.split("&")?.forEach { pair -> val kv = pair.split("=", limit = 2); query[URLDecoder.decode(kv[0], "UTF-8")] = if (kv.size == 2) URLDecoder.decode(kv[1], "UTF-8") else "" }
            if (!MessageDigest.isEqual(query.getOrDefault("token", "").toByteArray(), token(this).toByteArray())) { send(s, 403, "text/plain", "访问链接无效".toByteArray()); return }
            val path = uri.path; var type = "application/json"
            val body = when {
                path == "/restart" && parts[0] == "POST" -> { handleCommand(port, true); "{}".toByteArray() }
                parts[0] != "GET" -> { send(s, 405, "text/plain", byteArrayOf()); return }
                path == "/status" -> status().toString().toByteArray()
                path == "/apps" -> { if (query.containsKey("refresh")) catalog?.refresh(); JSONObject().put("ready", catalog != null).put("apps", catalog?.json() ?: JSONArray()).toString().toByteArray() }
                path == "/icon" -> { type = "image/png"; (catalog ?: throw FileNotFoundException()).icon(query.getOrDefault("package", "")) }
                path == "/logs" -> {
                    val after = maxOf(0L, query.getOrDefault("after", "0").toLong()); val entries = buffer.after(after, 500); val records = JSONArray()
                    for (e in entries) records.put(JSONObject().put("id", e.id).put("time", e.time).put("uid", e.uid).put("pid", e.pid).put("tid", e.tid).put("level", e.level).put("tag", e.tag).put("message", e.message).put("package", e.packageName).put("process", e.process).put("line", e.raw).put("bytes", e.bytes))
                    status().put("records", records).put("cursor", entries.lastOrNull()?.id ?: after).put("oldest", buffer.oldest()).put("latest", buffer.latest()).put("dropped", buffer.dropped()).put("bytes", buffer.bytes()).put("count", buffer.size()).toString().toByteArray()
                }
                else -> {
                    val file = if (path == "/") "index.html" else path.substring(1)
                    if (file !in setOf("index.html", "app.js", "query.js", "filter-worker.js", "style.css")) { send(s, 404, "text/plain", byteArrayOf()); return }
                    type = if (file.endsWith(".css")) "text/css" else if (file.endsWith(".js")) "text/javascript" else "text/html"
                    assets.open("web/$file").use { it.readBytes() }
                }
            }
            send(s, 200, type, body)
        } } catch (_: Exception) { }
    }
    private fun status() = JSONObject().put("session", started).put("started", started).put("now", System.currentTimeMillis()).put("uid", android.os.Process.myUid()).put("port", port).put("readLogsGranted", hasReadLogs(this)).put("readerAlive", reader?.isAlive == true).put("lastLogAt", lastLogAt).put("lastForeignAt", lastForeignAt).put("error", error)
    private fun send(socket: Socket, code: Int, type: String, body: ByteArray) {
        val headers = "HTTP/1.1 $code ${if (code == 200) "OK" else "Error"}\r\nContent-Type: $type${if (type.startsWith("image/")) "" else "; charset=utf-8"}\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().write(headers.toByteArray(Charsets.US_ASCII)); socket.getOutputStream().write(body)
    }
    fun listening() = server?.isClosed == false
    @Synchronized override fun onDestroy() { running = false; active = null; reader?.destroy(); verificationReader?.destroy(); try { server?.close() } catch (_: IOException) { }; clients.shutdownNow(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        @Volatile @JvmField var active: LogService? = null
        @Volatile @JvmField var lastFailure = ""
        @JvmStatic @Synchronized fun token(context: Context): String {
            val prefs = context.getSharedPreferences("server", 0)
            return prefs.getString("token", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("token", it).commit() }
        }
        @JvmStatic fun configuredPort(context: Context) = context.getSharedPreferences("server", 0).getInt("port", 8765)
        @JvmStatic fun hasReadLogs(context: Context) = context.checkSelfPermission(android.Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED
    }
}
