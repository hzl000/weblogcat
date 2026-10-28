package dev.weblogcat.probe

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.io.ByteArrayInputStream
import java.io.IOException
import java.math.BigInteger
import java.net.Inet4Address
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.security.auth.x500.X500Principal
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/** 本机 ADB 客户端只连接本机 Wi-Fi 地址，只执行固定的权限授权命令。 */
class LocalAdb private constructor(context: Context) : AbsAdbConnectionManager() {
    private val adbPrivateKey: PrivateKey
    private val adbCertificate: Certificate
    init {
        setApi(Build.VERSION.SDK_INT); setTimeout(8, TimeUnit.SECONDS); setThrowOnUnauthorised(true)
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(KEY_ALIAS)) {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); generateKey()
            }
        }
        val wrappingKey = store.getKey(KEY_ALIAS, null) as SecretKey
        val prefs = context.getSharedPreferences("wireless_adb", Context.MODE_PRIVATE)
        val encrypted = prefs.getString("key", null); val iv = prefs.getString("iv", null); val cert = prefs.getString("cert", null)
        if (encrypted != null && iv != null && cert != null) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))) }
            val clear = cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP))
            adbPrivateKey = try { KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(clear)) } finally { clear.fill(0) }
            adbCertificate = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(Base64.decode(cert, Base64.NO_WRAP)))
        } else {
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair(); adbPrivateKey = pair.private
            val name = X500Principal("CN=WebLogcat"); val now = System.currentTimeMillis()
            val builder = JcaX509v3CertificateBuilder(name, BigInteger(128, SecureRandom()).abs().add(BigInteger.ONE), Date(now - 86400000L), Date(now + 315360000000L), name, pair.public)
            adbCertificate = JcaX509CertificateConverter().getCertificate(builder.build(JcaContentSignerBuilder("SHA256withRSA").build(adbPrivateKey)))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, wrappingKey) }
            val clear = adbPrivateKey.encoded; val wrapped = try { cipher.doFinal(clear) } finally { clear.fill(0) }
            if (!prefs.edit().putString("key", Base64.encodeToString(wrapped, Base64.NO_WRAP)).putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).putString("cert", Base64.encodeToString(adbCertificate.encoded, Base64.NO_WRAP)).commit()) throw IOException("无法保存配对密钥")
        }
    }
    override fun getPrivateKey() = adbPrivateKey
    override fun getCertificate() = adbCertificate
    override fun getDeviceName() = "WebLogcat"
    @Synchronized fun pairLocal(context: Context, port: Int, code: String) {
        require(port in 1..65535 && code.matches(Regex("[0-9]{6}"))) { "请输入有效的配对端口和六位配对码" }
        if (!pair(localWifiAddress(context), port, code)) throw IOException("配对失败，请检查端口和配对码")
    }
    @Synchronized fun grantLocal(context: Context, port: Int) {
        require(port in 1..65535) { "请输入有效的无线调试连接端口" }
        val name = context.packageName; if (!name.matches(Regex("[A-Za-z0-9_.]+"))) throw SecurityException("包名无效")
        disconnect()
        try {
            if (!connect(localWifiAddress(context), port)) throw IOException("无法连接本机无线调试，请检查连接端口")
            // 权限变化会终止应用进程，后台 shell 负责重新打开首页。
            val open = "am start --activity-clear-top --activity-single-top -n $name/.MainActivity --ez wireless_grant_finished true"
            val script = "sleep 1; pm grant $name android.permission.READ_LOGS; result=\$?; sleep 2; $open --ez wireless_grant_success \$([ \$result -eq 0 ] && echo true || echo false)"
            // 保持外层 shell 存活，避免后台任务尚未启动就被终止。
            openStream("shell:nohup sh -c '$script' </dev/null >/dev/null 2>&1 & sleep 5").use { stream ->
                val input = stream.openInputStream(); val data = ByteArray(256); while (input.read(data) != -1) { }
            }
        } finally { disconnect() }
    }
    companion object {
        private const val KEY_ALIAS = "weblogcat_adb_key_wrap"
        private var instance: LocalAdb? = null
        @JvmStatic @Synchronized fun get(context: Context): LocalAdb = instance ?: LocalAdb(context.applicationContext).also { instance = it }
        @JvmStatic fun localWifiAddress(context: Context): String {
            val manager = context.getSystemService(ConnectivityManager::class.java)
            for (network in manager.allNetworks) {
                val caps = manager.getNetworkCapabilities(network) ?: continue
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
                val links = manager.getLinkProperties(network) ?: continue
                for (link in links.linkAddresses) { val ip = link.address; if (ip is Inet4Address && !ip.isLoopbackAddress && !ip.isLinkLocalAddress) return ip.hostAddress!! }
            }
            throw IOException("未找到本机 Wi-Fi 地址，请连接 Wi-Fi 并开启无线调试")
        }
    }
}
