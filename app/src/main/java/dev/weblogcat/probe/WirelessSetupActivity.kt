package dev.weblogcat.probe

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WirelessSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { AppTheme { WirelessSetup() } } }
    private fun showPairingInstructions(): String? {
        if (!getSystemService(NotificationManager::class.java).areNotificationsEnabled()) return "通知已关闭，请在应用通知设置中开启，或使用分屏输入配对信息。"
        // 发起新配对时清除旧的结果，避免把上一次成功误认为本次结果。
        getSharedPreferences("wireless_adb", 0).edit().putString("pair_status", "等待输入配对端口和配对码。").apply()
        PairingNotifications.show(this, "打开无线调试的配对码页面后，在此通知中输入配对端口和六位配对码。", true)
        return try { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)); null } catch (_: ActivityNotFoundException) { "请手动打开系统设置中的开发者选项。" }
    }
    @Composable private fun WirelessSetup() {
        var pairPort by remember { mutableStateOf("") }; var code by remember { mutableStateOf("") }; var connectPort by remember { mutableStateOf("") }
        var operation by remember { mutableStateOf("") }; var working by remember { mutableStateOf(false) }
        var tick by remember { mutableIntStateOf(0) }
        val scope = rememberCoroutineScope()
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> operation = if (ok) showPairingInstructions().orEmpty() else "通知未获允许，可在系统设置允许通知，或用分屏方式输入配对信息。" }
        LaunchedEffect(Unit) { while (true) { tick++; delay(1000) } }
        @Suppress("UNUSED_EXPRESSION") tick
        val granted = LogService.hasReadLogs(this)
        val pairStatus = getSharedPreferences("wireless_adb", 0).getString("pair_status", "尚未配对").orEmpty()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PageHeader("无线调试授权", "Android 11 及以上 · 无需电脑") { finish() }
                Text(if (granted) "● READ_LOGS 已授权" else "○ READ_LOGS 尚未授权", color = MaterialTheme.colorScheme.primary)
                if (Build.VERSION.SDK_INT < 30) Text("当前系统不支持本机无线调试配对，请返回设置页使用电脑 ADB 授权。") else {
                    PageCard {
                        Text("01   配对本机无线调试", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("开启无线调试后，选择“使用配对码配对设备”。展开 WebLogcat 通知，输入配对端口和六位配对码，中间用空格分隔。", fontSize = 14.sp)
                        FilledTonalButton(onClick = { if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else operation = showPairingInstructions().orEmpty() }, modifier = Modifier.fillMaxWidth()) { Text("打开开发者选项并显示配对通知") }
                        Text(pairStatus, fontSize = 14.sp)
                        Text("也可使用分屏或悬浮窗直接输入：", fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(pairPort, { pairPort = it }, label = { Text("配对端口") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                            OutlinedTextField(code, { code = it }, label = { Text("六位配对码") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.weight(1f))
                        }
                        FilledTonalButton(enabled = !working, onClick = {
                            val port = pairPort.toIntOrNull()
                            if (port == null || port !in 1..65535 || !code.matches(Regex("[0-9]{6}"))) operation = "请输入有效的配对端口和六位配对码。"
                            else { working = true; operation = "正在配对…"; val currentCode = code; scope.launch { val result = withContext(Dispatchers.IO) { try { LocalAdb.get(this@WirelessSetupActivity).pairLocal(this@WirelessSetupActivity, port, currentCode); true } catch (_: Exception) { false } catch (_: LinkageError) { false } }; code = ""; val message = if (result) "配对成功，请输入无线调试主页的连接端口。" else "配对失败，请检查无线调试、配对端口和配对码。"; getSharedPreferences("wireless_adb", 0).edit().putBoolean("paired", result).putString("pair_status", message).apply(); operation = message; working = false } }
                        }, modifier = Modifier.fillMaxWidth()) { Text("使用上面信息配对") }
                    }
                    PageCard {
                        Text("02   授权读取日志", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("配对后关闭配对弹窗，在无线调试主页查看“IP 地址和端口”，输入其中的连接端口。它与配对端口不同。", fontSize = 14.sp)
                        OutlinedTextField(connectPort, { connectPort = it }, label = { Text("无线调试连接端口") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        Button(enabled = !working, onClick = {
                            val port = connectPort.toIntOrNull()
                            if (port == null || port !in 1..65535) operation = "请输入有效的无线调试连接端口。"
                            else if (granted) operation = "READ_LOGS 已授权。"
                            else { working = true; operation = "正在授权。系统可能短暂关闭应用，完成后会自动返回首页…"; scope.launch { val result = withContext(Dispatchers.IO) { try { LocalAdb.get(this@WirelessSetupActivity).grantLocal(this@WirelessSetupActivity, port); true } catch (_: Exception) { false } catch (_: LinkageError) { false } }; operation = if (LogService.hasReadLogs(this@WirelessSetupActivity)) "READ_LOGS 已授权。" else if (result) "未检测到授权结果，请检查无线调试后重试。" else "授权未完成，请检查无线调试和连接端口后重试。"; working = false } }
                        }, modifier = Modifier.fillMaxWidth()) { Text("连接本机并授权 READ_LOGS") }
                        if (operation.isNotEmpty()) Text(operation, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        Text("授权后可关闭无线调试。启动服务时，如系统询问日志访问，请选择允许。", fontSize = 13.sp)
                    }
                }
            }
        }
        if (working) AlertDialog(onDismissRequest = {}, title = { Text("正在处理") }, text = { Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { CircularProgressIndicator(); Text("正在连接本机，请稍候。系统可能短暂关闭应用。") } }, confirmButton = {})
    }
}
