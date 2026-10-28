package dev.weblogcat.probe

/** 严格解析通知中输入的配对端口和六位配对码。 */
class PairingInput private constructor(@JvmField val port: Int, @JvmField val code: String) {
    companion object {
        @JvmStatic fun parse(value: String?): PairingInput {
            val trimmed = value?.trim()
            require(trimmed != null && trimmed.matches(Regex("[0-9]{1,5}\\s+[0-9]{6}"))) { "请输入配对端口、空格和六位配对码" }
            val parts = trimmed.split(Regex("\\s+"))
            val port = parts[0].toInt()
            require(port in 1..65535) { "配对端口必须在 1–65535 之间" }
            return PairingInput(port, parts[1])
        }
    }
}
