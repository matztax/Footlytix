# Futlytix – R8-Regeln für den Release-Build

# Alle Datenklassen (Room-Tabellen und Backup-Format) unverändert lassen:
# Feldnamen landen im JSON-Backup und in der Datenbank.
-keep class de.tactics.app.data.** { *; }

# kotlinx.serialization (zusätzlich zu den mitgelieferten Regeln der Bibliothek)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class de.tactics.app.**$$serializer { *; }
-keepclassmembers class de.tactics.app.** { *** Companion; }
-keepclasseswithmembers class de.tactics.app.** { kotlinx.serialization.KSerializer serializer(...); }

# Lesbare Absturzberichte in der Play Console
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
