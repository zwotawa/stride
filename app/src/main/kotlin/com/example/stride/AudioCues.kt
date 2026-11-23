package com.example.stride

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import java.util.Locale

enum class CueType(val label: String, val description: String, val tones: List<Int>) {
    WARM_UP("Warm-up", "One low tone", listOf(ToneGenerator.TONE_DTMF_1)),
    RUN("Run", "Two high tones", listOf(ToneGenerator.TONE_DTMF_9, ToneGenerator.TONE_DTMF_9)),
    JOG("Jog", "One medium tone", listOf(ToneGenerator.TONE_DTMF_5)),
    RECOVERY("Recovery", "Two low tones", listOf(ToneGenerator.TONE_DTMF_1, ToneGenerator.TONE_DTMF_1)),
    COOL_DOWN("Cool-down", "High then low", listOf(ToneGenerator.TONE_DTMF_9, ToneGenerator.TONE_DTMF_1)),
    COMPLETE("Run complete", "Three rising tones", listOf(ToneGenerator.TONE_DTMF_1, ToneGenerator.TONE_DTMF_5, ToneGenerator.TONE_DTMF_9));

    companion object {
        fun forInterval(kind: String) = entries.firstOrNull { it.label == kind } ?: RUN
    }
}

data class AudioCue(val type: CueType, val words: String = type.label)

interface CuePlayer {
    val status: String
    fun play(cue: AudioCue)
    fun stop()
    fun release()
}

/** Owns application-context audio resources; survives activity recreation with the run model. */
internal class AndroidCuePlayer(context: Context) : CuePlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private var tone: ToneGenerator? = null
    private var speech: TextToSpeech? = null
    private var speechReady = false
    private var released = false
    private var version = 0L
    private var job: Job? = null
    private var holdsFocus = false
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change < 0) handler.post { stop() }
    }
    private val focusRequest = if (Build.VERSION.SDK_INT >= 26) {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusListener, handler).build()
    } else null

    override var status by mutableStateOf("Tones ready · preparing voice")
        private set

    init {
        speech = runCatching {
            TextToSpeech(context.applicationContext) { result ->
                // Initialization is asynchronous. Ignore a late callback after ViewModel disposal.
                handler.post {
                    if (!released) configureSpeech(result)
                }
            }
        }.getOrNull()
        if (speech == null) status = "Tones only · speech unavailable on this device"
    }

    private fun configureSpeech(result: Int) {
        val engine = speech ?: return
        if (result != TextToSpeech.SUCCESS) {
            status = "Tones only · speech unavailable on this device"
            return
        }
        // Announcements are English; require an installed offline English voice.
        val voice = runCatching {
            engine.voices?.firstOrNull { !it.isNetworkConnectionRequired && it.locale.language == Locale.ENGLISH.language }
        }.getOrNull()
        speechReady = voice != null && engine.setVoice(voice) == TextToSpeech.SUCCESS
        if (!speechReady) {
            status = "Tones only · install an English voice in Android text-to-speech settings"
            return
        }
        engine.setAudioAttributes(attributes)
        engine.setSpeechRate(1.05f)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = ended(utteranceId, false)
            @Deprecated("Legacy callback required by TextToSpeech")
            override fun onError(utteranceId: String?) = ended(utteranceId, true)
            override fun onError(utteranceId: String?, errorCode: Int) = ended(utteranceId, true)
        })
        status = "Tones + spoken interval names"
    }

    private fun ended(id: String?, failed: Boolean) {
        handler.post {
            if (!released && id == version.toString()) {
                if (failed) status = "Tones only · speech could not be played"
                abandonFocus()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun requestFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= 26) audio.requestAudioFocus(focusRequest!!)
        else audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        holdsFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return holdsFocus
    }

    @Suppress("DEPRECATION")
    private fun abandonFocus() {
        if (!holdsFocus) return
        if (Build.VERSION.SDK_INT >= 26) audio.abandonAudioFocusRequest(focusRequest!!)
        else audio.abandonAudioFocus(focusListener)
        holdsFocus = false
    }

    override fun play(cue: AudioCue) {
        if (released) return
        stop() // Replace stale cues, including speech, instead of building a queue.
        if (!runCatching { requestFocus() }.getOrDefault(false)) {
            status = "Audio busy · cue could not be played"
            return
        }
        val token = version
        job = scope.launch {
            if (tone == null) tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull()
            if (tone == null) status = "Tone unavailable · trying spoken cue"
            for (note in cue.type.tones) {
                runCatching { tone?.startTone(note, 130) }
                delay(200)
            }
            if (token != version || released) return@launch
            if (speechReady && speech?.speak(cue.words, TextToSpeech.QUEUE_FLUSH, null, token.toString()) == TextToSpeech.SUCCESS) {
                status = "Tones + spoken interval names"
                // A broken engine must not leave other media ducked indefinitely.
                delay(8000)
            } else if (tone == null) {
                status = "Audio unavailable on this device"
            }
            if (token == version) abandonFocus()
        }
    }

    override fun stop() {
        version++
        job?.cancel()
        job = null
        runCatching { tone?.stopTone() }
        runCatching { speech?.stop() }
        abandonFocus()
    }

    override fun release() {
        if (released) return
        stop()
        released = true
        scope.cancel()
        runCatching { tone?.release() }
        runCatching { speech?.shutdown() }
        tone = null
        speech = null
    }
}
