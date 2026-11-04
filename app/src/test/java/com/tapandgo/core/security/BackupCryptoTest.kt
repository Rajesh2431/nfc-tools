package com.tapandgo.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {
    private val pw = "correct horse".toCharArray()

    @Test
    fun `round trip with the right password`() {
        val enc = BackupCrypto.encrypt("""{"format":"tapandgo-export"}""", pw.copyOf(), iterations = 10_000)
        assertTrue(BackupCrypto.isEncrypted(enc))
        assertFalse(enc.contains("tapandgo-export\"}"))
        assertEquals(BackupCrypto.Result.Ok("""{"format":"tapandgo-export"}"""), BackupCrypto.decrypt(enc, pw.copyOf()))
    }

    @Test
    fun `wrong password and tampering are detected`() {
        val enc = BackupCrypto.encrypt("secret data", pw.copyOf(), iterations = 10_000)
        assertEquals(BackupCrypto.Result.WrongPasswordOrCorrupt, BackupCrypto.decrypt(enc, "wrong password".toCharArray()))
        val ct = Regex("\"ciphertext\":\"([^\"]+)\"").find(enc)!!.groupValues[1]
        val flipped = (if (ct[0] == 'A') 'B' else 'A') + ct.substring(1)
        assertEquals(BackupCrypto.Result.WrongPasswordOrCorrupt, BackupCrypto.decrypt(enc.replace(ct, flipped), pw.copyOf()))
    }

    @Test
    fun `salt and iv are random per export`() {
        val a = BackupCrypto.encrypt("x", pw.copyOf(), iterations = 10_000)
        val b = BackupCrypto.encrypt("x", pw.copyOf(), iterations = 10_000)
        assertFalse(a == b)
    }

    @Test
    fun `rejects weak parameters, short passwords and plain files`() {
        assertThrows(IllegalArgumentException::class.java) { BackupCrypto.encrypt("x", "short".toCharArray()) }
        assertEquals(BackupCrypto.Result.NotEncrypted, BackupCrypto.decrypt("""{"format":"tapandgo-export"}""", pw.copyOf()))
        val weak = BackupCrypto.encrypt("x", pw.copyOf(), iterations = 10_000).replace("\"iterations\":10000", "\"iterations\":1")
        assertEquals(BackupCrypto.Result.Unsupported, BackupCrypto.decrypt(weak, pw.copyOf()))
    }
}
