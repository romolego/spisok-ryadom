package com.spisokryadom.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Parcel
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.domain.*
import com.spisokryadom.app.ui.goallists.*
import com.spisokryadom.app.ui.bulk.shoppingListShareIntent
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class, shadows = [WindowsGoalShareProvider::class])
class Audit151ShareTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val directory get() = File(app.cacheDir, "goal-list-shares")
    @After fun cleanup() { directory.listFiles()?.forEach { it.delete() } }
    private fun read(uri: Uri): ByteArray = app.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
    private fun uri(intent: Intent) = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!

    @Test fun ordinaryMessageStaysInlineAndDoesNotCreateAnAttachment() {
        val text = GoalListTransfer.exportText(GoalListDocument("Ужин", listOf(GoalListItemDraft("Огурцы"))))
        val send = goalShareIntent(app, "Ужин", text)
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals(text, send.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("Ужин", send.getStringExtra(Intent.EXTRA_SUBJECT))
        assertFalse(send.hasExtra(Intent.EXTRA_STREAM))
        assertNull(send.clipData)
        assertEquals(0, send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    @Test fun largeUnicodeV2MessageUsesReadableExactUtf8AttachmentWithinBinderBudget() {
        val document = GoalListDocument("Рецепт 🌻", List(10) {
            GoalListItemDraft("Позиция $it", .123456, "кг", 3.125, "З".repeat(10_000), comment = "К".repeat(10_000))
        })
        val text = GoalListTransfer.exportText(document)
        assertTrue(goalShareUsesFile(text))
        val send = goalShareIntent(app, document.name, text)
        assertFalse(send.hasExtra(Intent.EXTRA_TEXT))
        val source = uri(send)
        assertEquals("content", source.scheme)
        assertEquals(app.packageName + ".files", source.authority)
        assertEquals("goal_lists", source.pathSegments.first())
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertEquals(source, send.clipData!!.getItemAt(0).uri)
        assertArrayEquals(text.toByteArray(Charsets.UTF_8), read(source))
        assertEquals(document, GoalListTransfer.parse(read(source).toString(Charsets.UTF_8)).document)
        val parcel = Parcel.obtain()
        try { send.writeToParcel(parcel, 0); assertTrue("Intent parcel ${parcel.dataSize()}", parcel.dataSize() < 64 * 1024) }
        finally { parcel.recycle() }
    }
    @Test fun laterShareDoesNotOverwriteEarlierAttachmentAndRetainsRecentFiles() {
        val firstText = "Огурцы\n".repeat(10_000)
        val first = uri(goalShareIntent(app, "Первый", firstText))
        val recent = directory.listFiles()!!.single()
        recent.setLastModified(System.currentTimeMillis() - 24L * 60 * 60 * 1000)
        val secondText = "Помидоры\n".repeat(10_000)
        val second = uri(goalShareIntent(app, "Второй", secondText))
        assertNotEquals(first, second)
        assertArrayEquals(firstText.toByteArray(Charsets.UTF_8), read(first))
        assertArrayEquals(secondText.toByteArray(Charsets.UTF_8), read(second))
        assertTrue(recent.isFile)
    }
    @Test fun mainShoppingMessageUsesTheSameSafeAttachmentBoundary() {
        val text = "Енисейский привоз — павильон:\nСыр — 0,5 кг [Ужин]\n".repeat(10_000)
        val chooser = shoppingListShareIntent(app, text)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertFalse(send.hasExtra(Intent.EXTRA_TEXT))
        assertEquals("Список покупок", send.getStringExtra(Intent.EXTRA_SUBJECT))
        assertArrayEquals(text.toByteArray(Charsets.UTF_8), read(uri(send)))
        val parcel = Parcel.obtain()
        try { chooser.writeToParcel(parcel, 0); assertTrue(parcel.dataSize() < 64 * 1024) }
        finally { parcel.recycle() }
    }
    @Test fun cleanupExpiresOnlyOwnAttachmentsAfterSevenDays() {
        directory.mkdirs()
        val old = File(directory, "spisok-${UUID.randomUUID()}.txt").also { it.writeText("old"); it.setLastModified(System.currentTimeMillis() - 8L * 24 * 60 * 60 * 1000) }
        val unrelated = File(directory, "unrelated.txt").also { it.writeText("keep"); it.setLastModified(old.lastModified()) }
        goalShareIntent(app, "Новый", "Я".repeat(GOAL_SHARE_INLINE_CHARS + 1))
        assertFalse(old.exists())
        assertTrue(unrelated.isFile)
    }
}

// AndroidX FileProvider hardcodes '/' when comparing canonical paths. Windows-hosted
// Robolectric needs this narrow path bridge; real ContentResolver and descriptor IO remain.
@org.robolectric.annotation.Implements(androidx.core.content.FileProvider::class)
class WindowsGoalShareProvider {
    companion object {
        private val files = mutableMapOf<String, File>()
        @JvmStatic @org.robolectric.annotation.Implementation
        fun getUriForFile(context: android.content.Context, authority: String, file: File): Uri {
            val root = File(context.cacheDir, "goal-list-shares").canonicalFile
            require(file.canonicalPath.startsWith(root.path + File.separator))
            val uri = Uri.parse("content://$authority/goal_lists/${file.name}")
            files[uri.toString()] = file
            return uri
        }
    }
    @org.robolectric.annotation.Implementation
    fun openFile(uri: Uri, mode: String): android.os.ParcelFileDescriptor {
        val file = files[uri.toString()] ?: throw java.io.FileNotFoundException()
        return android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.parseMode(mode))
    }
}
