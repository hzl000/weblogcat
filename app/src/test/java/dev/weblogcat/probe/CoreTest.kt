package dev.weblogcat.probe

import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    private fun entry() = LogEntry.parse("1793111794.120 10001 123 456 I Test: hello")!!
    @Test fun parsesAndBoundsBuffers() {
        val shell = LogEntry.parse(" 1793111794.919 shell  1341  1341 I adbd    : hello: world")!!
        assertEquals(2000, shell.uid); assertEquals(1341, shell.pid); assertEquals("adbd", shell.tag); assertEquals("hello: world", shell.message)
        assertEquals(1010803, LogEntry.parse("1793111794.120 u10_a803 123 456 W Test: text")!!.uid)
        assertNull(LogEntry.parse("--------- beginning of main"))
        val count = LogBuffer(2, 100000); repeat(3) { count.add(entry()) }
        assertEquals(2, count.size()); assertEquals(2L, count.oldest()); assertEquals(3L, count.latest()); assertEquals(1L, count.dropped()); assertEquals(3L, count.after(2, 1)[0].id)
        val bytes = LogBuffer(100, 600); repeat(3) { bytes.add(entry()) }; assertTrue(bytes.bytes() <= 600 && bytes.dropped() > 0)
        val full = LogBuffer(); repeat(60000) { full.add(entry()) }; assertEquals(50000, full.size()); assertEquals(10000L, full.dropped()); assertEquals(10001L, full.oldest())
        val large = LogBuffer(); repeat(30000) { large.add(entry().apply { this.bytes = 4096 }) }; assertTrue(large.bytes() <= 32L * 1024 * 1024); assertEquals(8192, large.size()); assertEquals(21808L, large.dropped())
    }
    @Test fun pairingInputBounds() {
        val p = PairingInput.parse(" 37123  001234 "); assertEquals(37123, p.port); assertEquals("001234", p.code)
        for (value in listOf(null, "", "0 123456", "65536 123456", "12 12345", "12 abcdef", "12 123456 extra", "192.168.1.1 123456")) {
            try { PairingInput.parse(value); fail("意外接受无效输入") } catch (_: IllegalArgumentException) { }
        }
    }
}
