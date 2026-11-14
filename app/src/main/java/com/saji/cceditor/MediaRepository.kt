package com.saji.cceditor

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaAsset(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val durationMs: Long,
    val isVideo: Boolean,
    val dateAdded: Long
)

enum class GalleryFilter { ALL, VIDEOS, PHOTOS }

fun galleryPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_IMAGES
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

fun hasGalleryAccess(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= 34) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
    }
    if (Build.VERSION.SDK_INT >= 33) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
    }
    return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
}

suspend fun queryGallery(context: Context, filter: GalleryFilter): List<MediaAsset> = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val output = mutableListOf<MediaAsset>()
    val idColumn = MediaStore.MediaColumns._ID
    val nameColumn = MediaStore.MediaColumns.DISPLAY_NAME
    val mimeColumn = MediaStore.MediaColumns.MIME_TYPE
    val dateColumn = MediaStore.MediaColumns.DATE_ADDED

    if (filter != GalleryFilter.PHOTOS) {
        val projection = arrayOf(idColumn, nameColumn, mimeColumn, dateColumn, MediaStore.Video.Media.DURATION)
        runCatching {
            resolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, null, null, "${dateColumn} DESC")?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(idColumn)
                val nameIndex = cursor.getColumnIndexOrThrow(nameColumn)
                val mimeIndex = cursor.getColumnIndexOrThrow(mimeColumn)
                val dateIndex = cursor.getColumnIndexOrThrow(dateColumn)
                val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    output += MediaAsset(
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                        cursor.getString(nameIndex) ?: "Video",
                        cursor.getString(mimeIndex) ?: "video/*",
                        cursor.getLong(durationIndex), true, cursor.getLong(dateIndex)
                    )
                }
            }
        }
    }
    if (filter != GalleryFilter.VIDEOS) {
        val projection = arrayOf(idColumn, nameColumn, mimeColumn, dateColumn)
        runCatching {
            resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, "${dateColumn} DESC")?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(idColumn)
                val nameIndex = cursor.getColumnIndexOrThrow(nameColumn)
                val mimeIndex = cursor.getColumnIndexOrThrow(mimeColumn)
                val dateIndex = cursor.getColumnIndexOrThrow(dateColumn)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    output += MediaAsset(
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                        cursor.getString(nameIndex) ?: "Photo",
                        cursor.getString(mimeIndex) ?: "image/*",
                        6_000L, false, cursor.getLong(dateIndex)
                    )
                }
            }
        }
    }
    output.sortedByDescending { it.dateAdded }
}
