package com.khabir.app.presentation.common

import android.Manifest
import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.speech.SpeechRecognizer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSpeechRecognizer
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class ContinuousDictationTest {
    private lateinit var controller: ContinuousDictationController
    @Before fun setup() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        controller = ContinuousDictationController(app, {}, { true })
        controller.start()
        shadowOf(Looper.getMainLooper()).idle()
    }
    @After fun cleanup() { controller.release() }
    private fun speech() = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
    private fun result(text: String) = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
    }
    @Test fun finishWaitsForFinalAndIgnoresLateCallbacks() {
        val first = speech()
        first.triggerOnPartialResults(result("أقام المدعي"))
        var saved: String? = null
        var calls = 0
        controller.finish { saved = it; calls++ }
        assertNull(saved)
        first.triggerOnResults(result("أقام المدعي دعواه"))
        assertEquals("أقام المدعي دعواه", saved)
        first.triggerOnResults(result("نتيجة متأخرة"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
        assertEquals(1, calls)
    }
    @Test fun silenceStartsAnotherSessionWithoutLosingText() {
        val first = ShadowSpeechRecognizer.getLatestSpeechRecognizer()
        speech().triggerOnResults(result("الجملة الأولى"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        assertNotSame(first, ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        speech().triggerOnError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        speech().triggerOnResults(result("الجملة الثانية"))
        assertEquals("الجملة الأولى الجملة الثانية", controller.transcript)
        assertTrue(controller.isListening)
    }
    @Test fun busyRetriesAndManualRetryPreservesPartialText() {
        speech().triggerOnPartialResults(result("نص محفوظ"))
        speech().triggerOnError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertTrue(controller.isListening)
        speech().triggerOnError(SpeechRecognizer.ERROR_AUDIO)
        assertFalse(controller.isListening)
        controller.start(reset = false)
        shadowOf(Looper.getMainLooper()).idle()
        speech().triggerOnResults(result("تكملة"))
        assertEquals("نص محفوظ تكملة", controller.transcript)
    }
    @Test fun finishTimeoutKeepsPartialAndReleaseCancelsRestart() {
        speech().triggerOnPartialResults(result("آخر كلام"))
        var saved = ""
        controller.finish { saved = it }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
        assertEquals("آخر كلام", saved)
        assertFalse(controller.isListening)
        controller.start(reset = false)
        shadowOf(Looper.getMainLooper()).idle()
        speech().triggerOnError(SpeechRecognizer.ERROR_NO_MATCH)
        val last = ShadowSpeechRecognizer.getLatestSpeechRecognizer()
        controller.release()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertSame(last, ShadowSpeechRecognizer.getLatestSpeechRecognizer())
    }
}
