package com.spisokryadom.app

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.ui.goallists.GoalMessageDrafts
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
class GoalMessageDraftsTest {
    @Test fun largeMessagesRestoreByPrivateFileTokenWithoutEnteringSavedStateBundle() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val directory = File(app.cacheDir, "goal-message-test-${java.util.UUID.randomUUID()}")
        val state = SavedStateHandle(); val store = GoalMessageDrafts(state, directory)
        try {
            val text = "Огурцы — 0,5 кг\n".repeat(12_000)
            store.write("message", text)
            assertNull(state.get<String>("message"))
            val token = state.get<String>("message_file")!!
            assertTrue(File(directory, token).isFile)
            val restored = GoalMessageDrafts(SavedStateHandle(mapOf("message_file" to token)), directory)
            assertEquals(text, restored.read("message"))
            try { store.write("message", "Ж".repeat(600_000)); fail("The size limit must reject oversized text") } catch (_: IllegalArgumentException) { }
            assertEquals(text, store.read("message"))
            store.write("message", "Короткий текст")
            assertEquals("Короткий текст", state.get<String>("message")); assertNull(state.get<String>("message_file")); assertFalse(File(directory, token).exists())
            store.write("message", null); assertNull(store.read("message"))
            assertNull(GoalMessageDrafts(SavedStateHandle(mapOf("message_file" to "../outside.txt")), directory).read("message"))
        } finally { directory.listFiles()?.forEach { it.delete() }; directory.delete() }
    }
}
