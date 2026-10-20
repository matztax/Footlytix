package de.tactics.app

import android.content.Context

/** Einfache Einstellungen (Sperre, Auto-Backup, Backup-Passwort). */
class AppPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("futlytix_prefs", Context.MODE_PRIVATE)

    var lockEnabled: Boolean
        get() = sp.getBoolean("lock", false)
        set(v) { sp.edit().putBoolean("lock", v).apply() }

    var autoBackup: Boolean
        get() = sp.getBoolean("auto_backup", true)
        set(v) { sp.edit().putBoolean("auto_backup", v).apply() }

    var lastBackupDay: String
        get() = sp.getString("last_backup_day", "") ?: ""
        set(v) { sp.edit().putString("last_backup_day", v).apply() }

    var lastBackupTime: Long
        get() = sp.getLong("last_backup_time", 0L)
        set(v) { sp.edit().putLong("last_backup_time", v).apply() }

    var backupFolder: String?
        get() = sp.getString("backup_folder", null)
        set(v) { sp.edit().putString("backup_folder", v).apply() }

    /** Fehlermeldung der letzten Kopie in den Backup-Ordner (null = erfolgreich bzw. kein Ordner). */
    var lastFolderError: String?
        get() = sp.getString("last_folder_error", null)
        set(v) { sp.edit().putString("last_folder_error", v).apply() }

    /**
     * Passwort für Backups, die das Gerät verlassen (Backup-Ordner, Teilen, Export).
     * Liegt nur im privaten App-Speicher; die App-Daten selbst werden nicht in die Cloud gesichert.
     */
    var backupPassword: String?
        get() = sp.getString("backup_password", null)?.takeIf { it.isNotEmpty() }
        set(v) { sp.edit().putString("backup_password", v?.takeIf { it.isNotEmpty() }).apply() }
}
