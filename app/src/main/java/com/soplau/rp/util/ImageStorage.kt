package com.soplau.rp.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/** Copies gallery images into the app's private storage so avatars survive if the gallery item is removed. */
class ImageStorage(private val context: Context) {

    private val dir: File by lazy {
        File(context.filesDir, "avatars").apply { mkdirs() }
    }

    fun importAvatar(uri: Uri): String? {
        return runCatching {
            val target = File(dir, "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            target.absolutePath
        }.getOrNull()
    }

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).takeIf { it.parentFile == dir }?.delete() }
    }
}
