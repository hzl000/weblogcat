package dev.weblogcat.probe

import org.junit.Assert.*
import org.junit.Test

class AccessCodeTest {
    @Test fun codeExpiresAndCanOnlyBeUsedOnce() {
        var clock = 0L; var next = 12
        val codes = AccessCode({ clock }, { next++ })
        assertEquals("000012", codes.current().code)
        assertEquals(AccessCode.Result.INVALID, codes.verify("000013"))
        assertEquals(AccessCode.Result.APPROVED, codes.verify("000012"))
        assertEquals("000013", codes.current().code)
        assertEquals(AccessCode.Result.INVALID, codes.verify("000012"))
        clock += 120_000
        assertEquals("000014", codes.current().code)
        assertEquals(AccessCode.Result.INVALID, codes.verify("000013"))
    }
    @Test fun repeatedErrorsLockTemporarily() {
        var clock = 0L; var next = 123456
        val codes = AccessCode({ clock }, { next++ })
        repeat(4) { assertEquals(AccessCode.Result.INVALID, codes.verify("999999")) }
        assertEquals(AccessCode.Result.LOCKED, codes.verify("999999"))
        assertTrue(codes.current().locked)
        assertNull(codes.current().code)
        assertEquals(AccessCode.Result.LOCKED, codes.verify("123456"))
        clock += 30_000
        assertEquals("123457", codes.current().code)
        assertEquals(AccessCode.Result.APPROVED, codes.verify("123457"))
    }
}
