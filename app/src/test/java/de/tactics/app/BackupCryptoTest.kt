package de.tactics.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCryptoTest {
    // Wenige Iterationen, damit der Test schnell bleibt (die App nutzt 210 000)
    private val fast = 10_000
    private val sample = """{"version":6,"players":[{"id":1,"name":"Jürgen Müller – Ä/Ö/Ü ß ⚽"}]}"""

    @Test
    fun roundTrip() {
        val enc = BackupCrypto.encrypt(sample, "geheim123", fast)
        assertTrue(BackupCrypto.isEncrypted(enc))
        assertFalse(enc.contains("Müller"))
        assertEquals(sample, BackupCrypto.decrypt(enc, "geheim123"))
    }

    @Test
    fun plainJsonIsNotEncrypted() {
        assertFalse(BackupCrypto.isEncrypted(sample))
        assertFalse(BackupCrypto.isEncrypted(""))
    }

    @Test
    fun sameInputGivesDifferentCiphertext() {
        val a = BackupCrypto.encrypt(sample, "geheim123", fast)
        val b = BackupCrypto.encrypt(sample, "geheim123", fast)
        assertNotEquals(a, b) // zufälliges Salt und IV
    }

    @Test
    fun wrongPasswordIsDetected() {
        val enc = BackupCrypto.encrypt(sample, "geheim123", fast)
        try {
            BackupCrypto.decrypt(enc, "falsch")
            fail("Falsches Passwort wurde akzeptiert")
        } catch (e: BackupCrypto.WrongPasswordException) {
            // erwartet
        }
    }

    @Test
    fun missingPasswordIsReported() {
        val enc = BackupCrypto.encrypt(sample, "geheim123", fast)
        try {
            BackupCrypto.decrypt(enc, null)
            fail("Ohne Passwort entschlüsselt")
        } catch (e: BackupCrypto.PasswordRequiredException) {
            // erwartet
        }
    }

    @Test
    fun tamperedDataIsRejected() {
        val enc = BackupCrypto.encrypt(sample, "geheim123", fast)
        val data = Regex("\"data\":\"([^\"]+)\"").find(enc)!!.groupValues[1]
        val flipped = (if (data[5] == 'A') 'B' else 'A') + ""
        val tampered = enc.replace(data, data.substring(0, 5) + flipped + data.substring(6))
        try {
            BackupCrypto.decrypt(tampered, "geheim123")
            fail("Manipulierte Datei wurde akzeptiert")
        } catch (e: BackupCrypto.WrongPasswordException) {
            // erwartet
        }
    }

    @Test
    fun tamperedIterationsAreRejected() {
        val enc = BackupCrypto.encrypt(sample, "geheim123", fast)
        val tampered = enc.replace("\"iter\":$fast", "\"iter\":${fast + 1}")
        try {
            BackupCrypto.decrypt(tampered, "geheim123")
            fail("Geänderte Parameter wurden akzeptiert")
        } catch (e: BackupCrypto.WrongPasswordException) {
            // erwartet
        }
    }
}
