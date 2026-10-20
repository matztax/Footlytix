package de.tactics.app

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sicherungen im App-Speicher:
 * - tägliche Sicherung (die letzten 7 bleiben erhalten), optional zusätzlich in einen gewählten Ordner
 * - Sicherheitskopie direkt vor jeder Wiederherstellung (die letzten 3 bleiben erhalten)
 *
 * Interne Dateien liegen unverschlüsselt im privaten App-Speicher (wie die Datenbank selbst).
 * Alles, was das Gerät verlässt (Ordner, Teilen), wird verschlüsselt, sobald ein Backup-Passwort gesetzt ist.
 */
object AutoBackup {
    const val DAILY_PREFIX = "futlytix-"
    const val SAFETY_PREFIX = "vor-wiederherstellung-"
    private const val KEEP_DAILY = 7
    private const val KEEP_SAFETY = 3

    private fun dir(ctx: Context): File = File(ctx.filesDir, "backups").apply { mkdirs() }

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date())

    /** Alle Sicherungen, neueste zuerst. */
    fun list(ctx: Context): List<File> =
        dir(ctx).listFiles { f -> f.name.endsWith(".json") }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun isSafetyCopy(f: File): Boolean = f.name.startsWith(SAFETY_PREFIX)

    /** Anzeigename für die Liste in den Einstellungen. */
    fun label(f: File): String =
        if (isSafetyCopy(f)) "Vor Wiederherstellung " + f.name.removePrefix(SAFETY_PREFIX).removeSuffix(".json")
        else f.name.removePrefix(DAILY_PREFIX).removeSuffix(".json")

    /** Tägliche Sicherung schreiben und ggf. in den gewählten Ordner kopieren. */
    fun write(ctx: Context, json: String): File {
        val file = File(dir(ctx), DAILY_PREFIX + today() + ".json")
        writeAtomically(file, json)
        prune(ctx)
        copyToFolder(ctx, file.name, json)
        return file
    }

    /** Sicherheitskopie des aktuellen Stands, bevor eine Wiederherstellung alles ersetzt. */
    fun writeSafetyCopy(ctx: Context, json: String): File {
        val stamp = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.GERMANY).format(Date())
        val file = File(dir(ctx), "$SAFETY_PREFIX$stamp.json")
        writeAtomically(file, json)
        prune(ctx)
        return file
    }

    /**
     * Datei, die geteilt werden darf: mit Passwort eine verschlüsselte Kopie im Export-Cache,
     * sonst die Sicherung selbst.
     */
    fun shareableCopy(ctx: Context, f: File): File {
        val password = AppPrefs(ctx).backupPassword ?: return f
        val text = f.readText()
        val out = File(File(ctx.cacheDir, "exports").apply { mkdirs() }, f.name)
        out.writeText(if (BackupCrypto.isEncrypted(text)) text else BackupCrypto.encrypt(text, password))
        return out
    }

    private fun prune(ctx: Context) {
        val all = list(ctx)
        all.filter { it.name.startsWith(DAILY_PREFIX) }.drop(KEEP_DAILY).forEach { it.delete() }
        all.filter { isSafetyCopy(it) }.drop(KEEP_SAFETY).forEach { it.delete() }
    }

    /** Erst in eine Temp-Datei schreiben, dann umbenennen: ein Absturz hinterlässt keine halbe Sicherung. */
    private fun writeAtomically(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (file.exists()) file.delete()
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }

    private fun copyToFolder(ctx: Context, name: String, json: String) {
        val prefs = AppPrefs(ctx)
        val folder = prefs.backupFolder ?: run { prefs.lastFolderError = null; return }
        val result = runCatching {
            val password = prefs.backupPassword
            val content = if (password != null) BackupCrypto.encrypt(json, password) else json
            val tree = DocumentFile.fromTreeUri(ctx, Uri.parse(folder))
                ?: error("Ordner nicht mehr erreichbar")
            if (!tree.canWrite()) error("Keine Schreibrechte im Ordner")
            tree.findFile(name)?.delete()
            val target = tree.createFile("application/json", name) ?: error("Datei konnte nicht angelegt werden")
            val stream = ctx.contentResolver.openOutputStream(target.uri) ?: error("Datei konnte nicht geöffnet werden")
            stream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
        }
        prefs.lastFolderError = result.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }
    }
}
