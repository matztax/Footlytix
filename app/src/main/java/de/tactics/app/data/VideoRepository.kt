package de.tactics.app.data

import androidx.room.withTransaction

/** Video-Bibliothek, Zeichnungen und Marker. */
class VideoRepository(private val db: AppDatabase) {
    private val dao = db.dao()

    suspend fun saveVideo(v: VideoItem) { dao.upsertVideo(v) }

    suspend fun deleteVideo(v: VideoItem) = db.withTransaction {
        dao.deleteVideo(v)
        dao.deleteMarkersOfVideo(v.id)
        if (dao.countVideosWithUri(v.uri) == 0) dao.deleteAnnotationsForUri(v.uri)
    }

    /**
     * Verknüpft ein Video mit einer neuen Datei (z. B. nach einer Wiederherstellung auf einem neuen Gerät).
     * Zeichnungen hängen an der Datei-Adresse und ziehen mit um; Marker hängen am Video und bleiben erhalten.
     */
    suspend fun relinkVideo(v: VideoItem, newUri: String) = db.withTransaction {
        if (newUri == v.uri) return@withTransaction
        dao.moveVideoUri(v.uri, newUri)
        dao.moveAnnotationUri(v.uri, newUri)
    }

    suspend fun saveAnnotation(a: VideoAnnotation) { dao.upsertAnnotation(a) }
    suspend fun deleteAnnotation(a: VideoAnnotation) { dao.deleteAnnotation(a) }
    suspend fun saveMarker(m: VideoMarker) { dao.upsertMarker(m) }
    suspend fun deleteMarker(m: VideoMarker) { dao.deleteMarker(m) }
}
