package dev.weblogcat.probe

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build

/** 配对码只通过通知输入或当前页面传递，不写入日志或持久化。 */
object PairingNotifications {
    const val CHANNEL = "wireless_pairing"
    const val ID = 35
    const val INPUT = "pairing_input"
    @JvmStatic fun channel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "无线调试配对", NotificationManager.IMPORTANCE_HIGH))
    }
    @JvmStatic fun prompt(context: Context, status: String, input: Boolean): Notification {
        channel(context)
        val open = Intent(context, WirelessSetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val content = PendingIntent.getActivity(context, 35, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentTitle("WebLogcat 无线配对").setContentText(status).setStyle(Notification.BigTextStyle().bigText(status)).setContentIntent(content).setAutoCancel(!input)
        if (input) {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val action = PendingIntent.getBroadcast(context, 36, Intent(context, PairingReceiver::class.java), flags)
            val reply = RemoteInput.Builder(INPUT).setLabel("配对端口 空格 六位配对码，例如 37123 123456").build()
            builder.addAction(Notification.Action.Builder(android.R.drawable.ic_menu_send, "输入端口和配对码", action).addRemoteInput(reply).build())
        }
        return builder.build()
    }
    @JvmStatic fun show(context: Context, status: String, input: Boolean) { context.getSystemService(NotificationManager::class.java).notify(ID, prompt(context, status, input)) }
    @JvmStatic fun result(context: Context, status: String, paired: Boolean) {
        context.getSharedPreferences("wireless_adb", 0).edit().putString("pair_status", status).putBoolean("paired", paired).apply(); show(context, status, !paired)
    }
}
