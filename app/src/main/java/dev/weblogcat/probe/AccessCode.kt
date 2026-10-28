package dev.weblogcat.probe

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

/** 管理本机显示的一次性验证码，限制错误尝试并在到期后轮换。 */
class AccessCode(
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val generate: () -> Int = { SecureRandom().nextInt(1_000_000) }
) {
    data class Display(val code: String?, val remainingSeconds: Long, val locked: Boolean)
    enum class Result { APPROVED, INVALID, LOCKED }
    private var code: String? = null
    private var previousCode: String? = null
    private var expiresAt = 0L
    private var lockedUntil = 0L
    private var failures = 0
    @Synchronized fun current(): Display {
        val time = now()
        if (time < lockedUntil) return Display(null, (lockedUntil - time + 999) / 1000, true)
        if (code == null || time >= expiresAt) {
            do { code = String.format(Locale.ROOT, "%06d", generate()) } while (code == previousCode)
            previousCode = code
            expiresAt = time + 120_000; failures = 0; lockedUntil = 0
        }
        return Display(code, (expiresAt - time + 999) / 1000, false)
    }
    @Synchronized fun verify(candidate: String): Result {
        val display = current()
        if (display.locked) return Result.LOCKED
        if (MessageDigest.isEqual(candidate.toByteArray(Charsets.UTF_8), display.code!!.toByteArray(Charsets.UTF_8))) {
            code = null; failures = 0; return Result.APPROVED
        }
        failures++
        if (failures >= 5) { code = null; lockedUntil = now() + 30_000; return Result.LOCKED }
        return Result.INVALID
    }
}
