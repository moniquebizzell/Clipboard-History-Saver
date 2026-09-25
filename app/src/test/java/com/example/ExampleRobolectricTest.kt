package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.ClipItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Pinboard", appName)
    }

    @Test
    fun `test category detection`() {
        assertEquals("url", ClipItem.detectCategory("https://example.com/test"))
        assertEquals("url", ClipItem.detectCategory("http://github.com"))
        assertEquals("email", ClipItem.detectCategory("support@example.com"))
        assertEquals("code", ClipItem.detectCategory("fun main() { println(\"test\") }"))
        assertEquals("code", ClipItem.detectCategory("{\"key\": \"value\"}"))
        assertEquals("text", ClipItem.detectCategory("Just a simple quick note for later."))
    }

    @Test
    fun `test word and char count`() {
        val clip = ClipItem(text = "Hello world from Android")
        assertEquals(4, clip.wordCount)
        assertEquals(24, clip.charCount)
    }
}
