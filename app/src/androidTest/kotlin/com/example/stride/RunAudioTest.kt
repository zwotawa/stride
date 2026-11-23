package com.example.stride

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RunAudioTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val prefs = application.getSharedPreferences("plan", 0)
    private var savedName: String? = null
    private var savedPlan: String? = null
    private var savedAudio: Boolean? = null
    private lateinit var model: RunModel
    private lateinit var player: RecordingPlayer
    private lateinit var store: ViewModelStore

    private class RecordingPlayer : CuePlayer {
        override val status = "Test player"
        val cues = mutableListOf<AudioCue>()
        var stopped = 0
        var released = false
        override fun play(cue: AudioCue) { cues.add(cue) }
        override fun stop() { stopped++ }
        override fun release() { released = true }
    }
    @Before fun setup() {
        savedName = prefs.getString("name", null)
        savedPlan = prefs.getString("intervals", null)
        savedAudio = if (prefs.contains("audioEnabled")) prefs.getBoolean("audioEnabled", true) else null
        prefs.edit().putString("intervals", "[]").putBoolean("audioEnabled", true).commit()
        instrumentation.runOnMainSync {
            player = RecordingPlayer()
            model = RunModel(application, player)
            store = ViewModelStore()
            store.put("audio-test", model)
        }
    }
    @After fun cleanup() {
        instrumentation.runOnMainSync { store.clear() }
        val edit = prefs.edit().putString("name", savedName).putString("intervals", savedPlan)
        if (savedAudio == null) edit.remove("audioEnabled") else edit.putBoolean("audioEnabled", savedAudio!!)
        edit.commit()
    }
    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun waitForCount(count: Int, timeoutMs: Long = 7000) {
        val end = android.os.SystemClock.elapsedRealtime() + timeoutMs
        var size = 0
        while (android.os.SystemClock.elapsedRealtime() < end) {
            onMain { size = player.cues.size }
            if (size >= count) return
            Thread.sleep(30)
        }
        fail("Expected $count cue events, received $size")
    }
    @Test fun startSkipPauseResumeMuteCompletionAndCleanup() {
        onMain {
            listOf("Warm-up", "Run", "Recovery", "Cool-down").forEach { model.update(null, Interval(it, 30)) }
            model.start()
            assertEquals(CueType.WARM_UP, player.cues.last().type)
            model.skip()
            assertEquals(CueType.RUN, player.cues.last().type)
            model.toggle()
            val count = player.cues.size
            assertTrue(player.stopped > 0)
            model.skip() // Paused transitions stay silent.
            assertEquals(count, player.cues.size)
            model.toggle()
            assertEquals(CueType.RECOVERY, player.cues.last().type)
            model.changeAudioEnabled(false)
            val mutedCount = player.cues.size
            model.skip()
            assertEquals(mutedCount, player.cues.size)
            model.changeAudioEnabled(true)
            model.toggle()
            model.toggle()
            assertEquals(CueType.COOL_DOWN, player.cues.last().type)
            model.skip()
            assertEquals(CueType.COMPLETE, player.cues.last().type)
            val endedCount = player.cues.size
            model.skip()
            assertEquals(endedCount, player.cues.size)
            model.finish()
            store.clear()
            assertTrue(player.released)
        }
    }
    @Test fun automaticGroupRoundsAndRepetitionsAnnounceExactlyOnce() {
        onMain {
            model.update(null, Interval("Jog", 1, repetitions = 2))
            model.update(null, Interval("Recovery", 1))
            model.group(setOf(0, 1), "Intervals", 2)
            model.start()
        }
        waitForCount(7, 10000)
        onMain {
            assertEquals(listOf(CueType.JOG, CueType.JOG, CueType.RECOVERY, CueType.JOG, CueType.JOG, CueType.RECOVERY, CueType.COMPLETE), player.cues.map { it.type })
            assertTrue(player.cues[1].words.startsWith("Jog."))
            assertTrue(player.cues[1].words.contains("Repetition 2 of 2"))
            assertTrue(player.cues[3].words.contains("Round 2 of 2"))
        }
        Thread.sleep(350)
        onMain { assertEquals(7, player.cues.size) }
    }
    @Test fun mutePersistsPreviewsRespectStateAndEndCancels() {
        onMain {
            CueType.entries.forEach { model.previewCue(it) }
            assertEquals(CueType.entries.toList(), player.cues.map { it.type })
            model.changeAudioEnabled(false)
            model.previewCue(CueType.RUN)
            assertEquals(CueType.entries.size, player.cues.size)
            assertFalse(prefs.getBoolean("audioEnabled", true))
            model.update(null, Interval("Run", 30))
            model.start()
            assertEquals(CueType.entries.size, player.cues.size)
            model.changeAudioEnabled(true)
            model.previewCue(CueType.WARM_UP) // Previews cannot interrupt a workout.
            assertEquals(CueType.entries.size, player.cues.size)
            val previousStops = player.stopped
            model.finish()
            assertTrue(player.stopped > previousStops)
            val restored = RunModel(application, RecordingPlayer())
            val restoredStore = ViewModelStore()
            restoredStore.put("restored", restored)
            assertTrue(restored.audioEnabled)
            restoredStore.clear()
        }
    }
}
