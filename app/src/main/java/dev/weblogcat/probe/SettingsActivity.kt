package dev.weblogcat.probe

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { AppTheme { Settings() } } }
    @Composable private fun Settings() {
        var port by remember { mutableStateOf(LogService.configuredPort(this).toString()) }
        var error by remember { mutableStateOf(false) }
        val version = remember {
            val info = packageManager.getPackageInfo(packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
            "${info.versionName ?: "未知"} ($code)"
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PageHeader("设置", "管理服务连接与日志访问权限") { finish() }
                PageCard {
                    Text("服务连接", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = port, onValueChange = { port = it; error = false }, label = { Text("服务端口") }, singleLine = true, isError = error, supportingText = if (error) ({ Text("请输入 1024–65535 的端口") }) else null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    FilledTonalButton(onClick = {
                        val value = port.toIntOrNull()
                        if (value == null || value !in 1024..65535) error = true
                        else { if (LogService.active == null) getSharedPreferences("server", 0).edit().putInt("port", value).apply() else startForegroundService(Intent(this@SettingsActivity, LogService::class.java).setAction("server").putExtra("port", value)); Toast.makeText(this@SettingsActivity, "端口已保存", Toast.LENGTH_SHORT).show() }
                    }, modifier = Modifier.fillMaxWidth()) { Text("保存端口") }
                    Text("修改端口后，正在运行的网页服务会重新监听。", fontSize = 13.sp)
                }
                PageCard {
                    Text("日志访问", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("在本机完成无线调试授权，或重新请求系统日志访问。", fontSize = 13.sp)
                    FilledTonalButton(onClick = { startActivity(Intent(this@SettingsActivity, WirelessSetupActivity::class.java)) }, modifier = Modifier.fillMaxWidth()) { Text("无线调试授权") }
                    FilledTonalButton(onClick = { if (!LogService.hasReadLogs(this@SettingsActivity)) Toast.makeText(this@SettingsActivity, "请先授予 READ_LOGS 权限", Toast.LENGTH_LONG).show() else { startForegroundService(Intent(this@SettingsActivity, LogService::class.java).setAction("restart").putExtra("port", LogService.configuredPort(this@SettingsActivity))); Toast.makeText(this@SettingsActivity, "正在请求日志访问", Toast.LENGTH_SHORT).show() } }, modifier = Modifier.fillMaxWidth()) { Text("重新请求日志访问") }
                    androidx.compose.foundation.text.selection.SelectionContainer { Text("电脑授权命令\nadb shell pm grant $packageName android.permission.READ_LOGS", fontSize = 12.sp) }
                }
                PageCard {
                    Text("关于", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("当前版本  $version", fontSize = 14.sp)
                }
            }
        }
    }
}
