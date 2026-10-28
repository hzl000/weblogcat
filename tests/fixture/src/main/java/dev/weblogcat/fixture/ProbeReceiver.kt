package dev.weblogcat.fixture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra("text") ?: "FIXTURE"
        val count = intent.getIntExtra("count", 1).coerceIn(1, 10000)
        for (i in 0 until count) Log.i("WebLogcatProbe", "$text main $i")
        Log.w("Network", "$text timeout 42"); Log.d("Noise", "$text noisy")
        if (intent.getBooleanExtra("stack", false)) {
            Log.e("AndroidRuntime", "FATAL EXCEPTION: fixture"); Log.e("AndroidRuntime", "java.lang.IllegalStateException: $text")
            Log.e("AndroidRuntime", "    at dev.weblogcat.fixture.ProbeReceiver.onReceive(ProbeReceiver.kt:1)"); Log.e("AndroidRuntime", "Caused by: java.io.IOException: test cause")
        }
    }
}
