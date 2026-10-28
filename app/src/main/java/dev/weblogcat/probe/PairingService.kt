package dev.weblogcat.probe

import android.app.Service
import android.content.Intent
import android.os.IBinder

class PairingService : Service() {
    override fun onCreate() { super.onCreate(); startForeground(PairingNotifications.ID, PairingNotifications.prompt(this, "正在连接本机配对端口…", false)) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) { stopSelf(); return START_NOT_STICKY }
        val port = intent.getIntExtra("port", 0); val code = intent.getStringExtra("code").orEmpty()
        Thread({
            var success: Boolean
            var message: String
            try { LocalAdb.get(this).pairLocal(this, port, code); success = true; message = "配对成功。返回 WebLogcat，输入无线调试页面的连接端口并点击授权。" }
            catch (e: Exception) { success = false; message = "配对失败（${e.javaClass.simpleName}），请确认无线调试、端口和配对码后重试。" }
            catch (e: LinkageError) { success = false; message = "配对失败（${e.javaClass.simpleName}），请确认无线调试、端口和配对码后重试。" }
            getSharedPreferences("wireless_adb", 0).edit().putString("pair_status", message).putBoolean("paired", success).apply()
            stopForeground(STOP_FOREGROUND_REMOVE); PairingNotifications.show(this, message, !success); stopSelf(startId)
        }, "wireless-pair").start()
        return START_NOT_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
