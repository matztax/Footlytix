package de.tactics.app

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Verschlüsselung von Backup-Dateien mit einem Passwort (AES-256-GCM, Schlüssel per PBKDF2-HMAC-SHA256).
 *
 * Format einer verschlüsselten Datei (eine Zeile JSON, damit sie als .json-Datei handhabbar bleibt):
 * {"futlytixEncrypted":1,"iter":210000,"salt":"…","iv":"…","data":"…"}
 *
 * Reines JVM-Kotlin ohne Android-Abhängigkeiten, daher per Unit-Test prüfbar.
 */
object BackupCrypto {
    const val MARKER = "futlytixEncrypted"
    private const val FORMAT_VERSION = 1
    private const val ITERATIONS = 210_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    class PasswordRequiredException : Exception("Dieses Backup ist mit einem Passwort geschützt.")
    class WrongPasswordException : Exception("Das Passwort ist falsch oder die Datei ist beschädigt.")

    private val random = SecureRandom()

    /** true, wenn der Text eine verschlüsselte Backup-Datei ist. */
    fun isEncrypted(text: String): Boolean {
        val head = text.trimStart().take(64)
        return head.startsWith("{") && head.contains("\"$MARKER\"")
    }

    fun encrypt(plain: String, password: String, iterations: Int = ITERATIONS): String {
        require(password.isNotEmpty()) { "Leeres Passwort" }
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad(iterations))
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val b64 = Base64.getEncoder()
        return "{\"$MARKER\":$FORMAT_VERSION,\"iter\":$iterations," +
            "\"salt\":\"${b64.encodeToString(salt)}\"," +
            "\"iv\":\"${b64.encodeToString(iv)}\"," +
            "\"data\":\"${b64.encodeToString(data)}\"}"
    }

    /**
     * Entschlüsselt eine Datei aus [encrypt].
     * @throws PasswordRequiredException wenn kein Passwort übergeben wurde
     * @throws WrongPasswordException bei falschem Passwort oder manipulierter Datei
     */
    fun decrypt(text: String, password: String?): String {
        if (password.isNullOrEmpty()) throw PasswordRequiredException()
        val fields = parseFlatJson(text)
        val version = fields[MARKER]?.toIntOrNull() ?: throw IllegalArgumentException("Kein verschlüsseltes Backup")
        if (version > FORMAT_VERSION) {
            throw IllegalArgumentException("Dieses Backup stammt aus einer neueren App-Version. Bitte Futlytix aktualisieren.")
        }
        val iterations = fields["iter"]?.toIntOrNull()?.takeIf { it in 10_000..10_000_000 }
            ?: throw IllegalArgumentException("Ungültige Backup-Datei")
        val b64 = Base64.getDecoder()
        val salt = b64.decode(fields["salt"] ?: throw IllegalArgumentException("Ungültige Backup-Datei"))
        val iv = b64.decode(fields["iv"] ?: throw IllegalArgumentException("Ungültige Backup-Datei"))
        val data = b64.decode(fields["data"] ?: throw IllegalArgumentException("Ungültige Backup-Datei"))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad(iterations))
        val plain = try {
            cipher.doFinal(data)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException()
        }
        return String(plain, Charsets.UTF_8)
    }

    /** Die Parameter fließen als „zusätzliche Daten“ in die Prüfsumme ein und sind damit fälschungssicher. */
    private fun aad(iterations: Int): ByteArray = "$MARKER:$FORMAT_VERSION:$iterations".toByteArray(Charsets.UTF_8)

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /** Minimaler Parser für das eigene, flache Format (nur Zahlen und Base64-Strings, keine Escapes). */
    private fun parseFlatJson(text: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val re = Regex("\"([A-Za-z]+)\"\\s*:\\s*(\"([A-Za-z0-9+/=]*)\"|(-?\\d+))")
        re.findAll(text).forEach { m ->
            val key = m.groupValues[1]
            val value = if (m.groupValues[3].isNotEmpty() || m.groupValues[2].startsWith("\"")) m.groupValues[3] else m.groupValues[4]
            result[key] = value
        }
        return result
    }
}
