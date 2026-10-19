package com.spisokryadom.app.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

// Keep a generous margin below Binder's shared transaction budget, which uses UTF-16.
internal const val MESSAGE_SHARE_INLINE_CHARS = 32_768
internal fun messageShareUsesFile(text: String): Boolean = text.length > MESSAGE_SHARE_INLINE_CHARS

/** Each attachment is immutable: opening another chooser must not change an earlier message. */
internal fun messageShareIntent(context: Context, title: String, text: String): Intent {
    val result = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, title)
    if (!messageShareUsesFile(text)) return result.putExtra(Intent.EXTRA_TEXT, text)
    val bytes = text.toByteArray(Charsets.UTF_8)
    require(bytes.size <= 32 * 1024 * 1024) { "Сообщение больше 32 МиБ" }
    val directory = File(context.cacheDir, "goal-list-shares").also { require(it.isDirectory || it.mkdirs()) { "Не удалось подготовить файл списка" } }
    val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
    directory.listFiles()?.filter { it.isFile && it.name.matches(Regex("spisok-[a-fA-F0-9-]{36}\\.txt")) && it.lastModified() < cutoff }?.forEach { it.delete() }
    val file = File(directory, "spisok-${UUID.randomUUID()}.txt")
    try {
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        return result.putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply {
            clipData = ClipData.newRawUri(title, uri)
        }
    } catch (error: Exception) { file.delete(); throw error }
}
