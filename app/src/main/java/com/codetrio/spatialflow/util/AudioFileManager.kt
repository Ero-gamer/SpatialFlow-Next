@file:Suppress("DEPRECATION")

package com.codetrio.spatialflow.util

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object AudioFileManager {

    private const val TAG = "AudioFileManager"

    @JvmStatic
    fun getRealPathFromURI(context: Context, uri: Uri?): String? {
        if (uri == null) return null

        try {
            val fileName = getFileName(context, uri)
            val tempFile = File(
                context.cacheDir,
                "temp_${System.currentTimeMillis()}_$fileName"
            )

            context.contentResolver.openInputStream(uri).use { inputStream ->
                if (inputStream == null) {
                    Log.e(TAG, "Cannot open input stream from URI")
                    return null
                }
                FileOutputStream(tempFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                    }
                }
            }

            return tempFile.absolutePath

        } catch (e: IOException) {
            Log.e(TAG, "Error extracting file from URI: ${e.message}", e)
            return null
        }
    }

    @JvmStatic
    @JvmOverloads
    fun createOutputFile(context: Context, fileName: String, subFolder: String = "SpatialFlow/Songs"): File {
        var finalFileName = fileName
        if (!finalFileName.endsWith(".m4a", ignoreCase = true) &&
            !finalFileName.endsWith(".mp3", ignoreCase = true) &&
            !finalFileName.endsWith(".opus", ignoreCase = true) &&
            !finalFileName.endsWith(".flac", ignoreCase = true)
        ) {
            finalFileName += ".m4a"
        }

        val cleanName = finalFileName.replace(Regex("[^a-zA-Z0-9._\\s()\\[\\]-]"), "_")

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ - Create temp file in cache for FFmpeg processing
            val tempDir = File(context.cacheDir, "SpatialFlow_output")
            if (!tempDir.exists()) tempDir.mkdirs()

            val tempFile = File(tempDir, cleanName)
            Log.d(TAG, "Temp output file for processing: ${tempFile.absolutePath}")
            tempFile
        } else {
            // Android 9 and below - Direct file access to Downloads/subFolder
            val downloadsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                subFolder
            )
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val outputFile = File(downloadsDir, cleanName)
            Log.d(TAG, "Output file (legacy): ${outputFile.absolutePath}")
            outputFile
        }
    }

    @JvmStatic
    @JvmOverloads
    fun scanFile(context: Context, file: File, subFolder: String = "SpatialFlow/Songs") {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Copy to MediaStore Audio for Android 10+
            copyToMediaStore(context, file, subFolder)
        } else {
            // Trigger media scanner for older versions
            try {
                context.sendBroadcast(
                    android.content.Intent(
                        android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                        Uri.fromFile(file)
                    )
                )
                Log.d(TAG, "Media scan requested: ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "scanFile failed: ${e.message}")
            }
        }
    }

    @JvmStatic
    fun scanDeletedFile(context: Context, file: File) {
        try {
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                null,
                null
            )
        } catch (e: Exception) {
            Log.e(TAG, "scanDeletedFile failed: ${e.message}")
        }
    }

    private fun copyToMediaStore(context: Context, sourceFile: File, subFolder: String = "SpatialFlow/Songs") {
        if (!sourceFile.exists()) {
            Log.e(TAG, "Source file doesn't exist: ${sourceFile.absolutePath}")
            return
        }

        if (sourceFile.name.endsWith(".lrc", ignoreCase = true) ||
            sourceFile.name.endsWith(".ttml", ignoreCase = true) ||
            sourceFile.name.endsWith(".txt", ignoreCase = true)
        ) {
            Log.d(TAG, "Skipping MediaStore audio insertion for non-audio file: ${sourceFile.name}")
            return
        }

        // Get clean metadata from filename
        var nameWithoutExtension = sourceFile.name
        if (nameWithoutExtension.endsWith(".mp3", ignoreCase = true)) {
            nameWithoutExtension = nameWithoutExtension.substring(0, nameWithoutExtension.length - 4)
        } else if (nameWithoutExtension.endsWith(".m4a", ignoreCase = true)) {
            nameWithoutExtension = nameWithoutExtension.substring(0, nameWithoutExtension.length - 4)
        } else if (nameWithoutExtension.endsWith(".opus", ignoreCase = true)) {
            nameWithoutExtension = nameWithoutExtension.substring(0, nameWithoutExtension.length - 5)
        } else if (nameWithoutExtension.endsWith(".flac", ignoreCase = true)) {
            nameWithoutExtension = nameWithoutExtension.substring(0, nameWithoutExtension.length - 5)
        }

        // Split by " - " to get Title and Artist
        var title = nameWithoutExtension
        var artist = "Unknown Artist"
        if (nameWithoutExtension.contains(" - ")) {
            val parts = nameWithoutExtension.split(" - ".toRegex(), 2).toTypedArray()
            title = parts[0].trim { it <= ' ' }
            artist = parts[1].trim { it <= ' ' }
        }

        val mimeType = when {
            sourceFile.name.endsWith(".flac", ignoreCase = true) -> "audio/flac"
            sourceFile.name.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
            sourceFile.name.endsWith(".opus", ignoreCase = true) -> "audio/opus"
            else -> "audio/mp4"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
            put(MediaStore.MediaColumns.TITLE, title)
            put(MediaStore.MediaColumns.ARTIST, artist)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/$subFolder")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val itemUri = try {
            resolver.insert(collection, values)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert into MediaStore: ${e.message}", e)
            null
        }

        if (itemUri == null) {
            Log.e(TAG, "Failed to create MediaStore entry")
            return
        }

        try {
            FileInputStream(sourceFile).use { `in` ->
                resolver.openOutputStream(itemUri).use { out ->
                    if (out == null) {
                        Log.e(TAG, "Failed to open output stream")
                        return
                    }

                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (`in`.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
            }

            val targetFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "$subFolder/${sourceFile.name}"
            )
            if (targetFile.exists()) {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf(mimeType),
                    null
                )
            }

            Log.d(TAG, "File copied to MediaStore successfully: Downloads/$subFolder/${sourceFile.name}")

        } catch (e: IOException) {
            Log.e(TAG, "Error copying to MediaStore: ${e.message}", e)
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name: String? = null

        if ("content" == uri.scheme) {
            try {
                context.contentResolver.query(uri, null, null, null, null).use { cursor ->
                    if (cursor != null && cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                        name = cursor.getString(nameIndex)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting filename: ${e.message}")
            }
        }

        if (name == null) {
            name = uri.lastPathSegment
        }

        return name ?: "audio.m4a"
    }
}
