package com.mylibrary.app

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class Video(
    val id: Long,
    val title: String,
    val uri: Uri,
    val durationMs: Long,
    val dateAdded: Long
)

object Library {

    fun scan(context: Context): List<Video> {
        val list = mutableListOf<Video>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED
        )
        context.contentResolver.query(
            collection, projection, null, null,
            MediaStore.Video.Media.DATE_ADDED + " DESC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                list.add(
                    Video(
                        id = id,
                        title = clean(c.getString(nameCol) ?: "Video"),
                        uri = ContentUris.withAppendedId(collection, id),
                        durationMs = c.getLong(durCol),
                        dateAdded = c.getLong(dateCol)
                    )
                )
            }
        }
        return list
    }

    private fun clean(name: String): String =
        name.substringBeforeLast('.').replace('.', ' ').replace('_', ' ').trim()

    private fun prefs(context: Context) =
        context.getSharedPreferences("progress", Context.MODE_PRIVATE)

    fun getPos(context: Context, id: Long): Long = prefs(context).getLong("pos_$id", 0L)

    fun getLast(context: Context, id: Long): Long = prefs(context).getLong("last_$id", 0L)

    fun savePos(context: Context, id: Long, pos: Long) {
        prefs(context).edit()
            .putLong("pos_$id", pos)
            .putLong("last_$id", System.currentTimeMillis())
            .apply()
    }
}
