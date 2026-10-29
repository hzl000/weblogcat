package dev.weblogcat.probe

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File

internal fun apkQrBitmap(url: String): Bitmap {
    val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 600, 600)
    return Bitmap.createBitmap(600, 600, Bitmap.Config.RGB_565).apply {
        for (y in 0 until 600) for (x in 0 until 600) {
            setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
}

internal fun shareApkLink(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
    context.startActivity(Intent.createChooser(intent, "分享下载链接"))
}

internal fun shareApkQr(context: Context, bitmap: Bitmap) {
    val directory = File(context.cacheDir, "share").apply { mkdirs() }
    val image = File(directory, "weblogcat-download.png")
    image.outputStream().use { stream -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", image)
    val intent = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).apply {
        clipData = ClipData.newRawUri("二维码", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享下载二维码"))
}
