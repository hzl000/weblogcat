package dev.weblogcat.probe

import org.junit.Assert.*
import org.junit.Test

class UpdateCheckerTest {
    @Test fun 版本按数字段比较() {
        assertTrue(isNewerVersion("v0.3.10", "0.3.9"))
        assertFalse(isNewerVersion("v0.3.2", "0.3.2"))
        assertFalse(isNewerVersion("v0.3", "0.3.0"))
        assertFalse(isNewerVersion("v0.2.99", "0.3.0"))
    }
    @Test fun 无法解析版本时不当作最新版本() {
        assertThrows(IllegalArgumentException::class.java) { isNewerVersion("v0.4.0-beta", "0.3.2") }
    }
}
