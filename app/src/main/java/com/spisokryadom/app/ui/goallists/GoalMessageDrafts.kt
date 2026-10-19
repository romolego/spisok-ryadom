package com.spisokryadom.app.ui.goallists

import androidx.lifecycle.SavedStateHandle
import java.io.File

/** Large share/import messages stay out of Android's saved-instance-state Binder bundle. */
internal class GoalMessageDrafts(private val state: SavedStateHandle, private val directory: File?) {
    fun read(key: String): String? {
        state.get<String>(key)?.let { return it }
        val token = state.get<String>("${key}_file") ?: return null
        if (!validToken(token)) return null
        return runCatching { File(directory ?: return null, token).takeIf { it.length() <= MAX_BYTES }?.readText(Charsets.UTF_8) }.getOrNull()
    }
    fun write(key: String, text: String?) {
        val old = state.get<String>("${key}_file")
        val bytes = text?.toByteArray(Charsets.UTF_8)
        if (bytes != null && bytes.size > INLINE_BYTES && directory != null) {
            require(bytes.size <= MAX_BYTES) { "Сообщение больше 1 МБ" }
            directory.mkdirs()
            val token = old?.takeIf(::validToken) ?: java.util.UUID.randomUUID().toString() + ".txt"
            val target = File(directory, token)
            val pending = File(directory, "$token.pending")
            pending.writeBytes(bytes)
            java.nio.file.Files.move(pending.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            state["${key}_file"] = token
            state.remove<String>(key)
        } else {
            // Tests may construct a VM without a cache directory; never put a large payload in a Bundle.
            if (bytes == null || bytes.size <= INLINE_BYTES) state[key] = text else state.remove<String>(key)
            state.remove<String>("${key}_file")
            if (old != null && validToken(old)) directory?.let { File(it, old).delete() }
        }
    }
    companion object {
        private const val INLINE_BYTES = 16_384
        private const val MAX_BYTES = 1_048_576L
        private fun validToken(token: String) = Regex("[a-fA-F0-9-]{36}\\.txt").matches(token)
        fun directory(context: android.content.Context): File = File(context.cacheDir, "goal-list-messages").also { dir ->
            if (dir.isDirectory) dir.listFiles()?.filter { it.isFile && System.currentTimeMillis() - it.lastModified() > 7L * 24 * 60 * 60 * 1000 }?.forEach { it.delete() }
        }
    }
}
