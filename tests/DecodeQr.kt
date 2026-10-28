// 从实机截图解码二维码，验证图像中的链接，而非只验证生成输入。
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.io.File
import javax.imageio.ImageIO

fun main(args: Array<String>) {
    val image = ImageIO.read(File(args[0])); val width = image.width; val height = image.height
    val pixels = image.getRGB(0, 0, width, height, null, 0, width)
    val result = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels))))
    println(result.text)
    check(result.text == args[1]) { "二维码地址与实际监听地址不一致" }
}
