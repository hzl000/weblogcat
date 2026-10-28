package dev.weblogcat.fixture

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

class ProbeActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) { super.onCreate(state); setContent { Text("WebLogcat 日志测试源") }; Log.i("WebLogcatProbe", "FIXTURE_START") }
}
