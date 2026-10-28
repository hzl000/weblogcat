package dev.weblogcat.fixture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class RemoteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { Log.i("WebLogcatProbe", "${intent.getStringExtra("text")} remote") }
}
