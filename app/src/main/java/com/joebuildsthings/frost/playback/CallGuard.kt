package com.joebuildsthings.frost.playback

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * Detects phone and VoIP calls from the audio mode. Used only when "Keep playing during games"
 * is on: in that mode Frost does not hold audio focus, so this is what still pauses music for
 * calls. Needs no permission. API 31+ gets mode change callbacks, older releases poll once a second.
 */
class CallGuard(
    private val context: Context,
    private val onCallStarted: () -> Unit,
    private val onCallEnded: () -> Unit
) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var inCall = false
    private var modeListener: Any? = null

    private val poll = object : Runnable {
        override fun run() {
            check()
            if (running) handler.postDelayed(this, POLL_MS)
        }
    }

    fun start() {
        if (running) return
        running = true
        inCall = isCallMode(audio.mode)
        if (Build.VERSION.SDK_INT >= 31) {
            registerModeListener()
        } else {
            handler.postDelayed(poll, POLL_MS)
        }
    }

    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(poll)
        if (Build.VERSION.SDK_INT >= 31) unregisterModeListener()
    }

    @RequiresApi(31)
    private fun registerModeListener() {
        val listener = AudioManager.OnModeChangedListener { check() }
        audio.addOnModeChangedListener(ContextCompat.getMainExecutor(context), listener)
        modeListener = listener
    }

    @RequiresApi(31)
    private fun unregisterModeListener() {
        (modeListener as? AudioManager.OnModeChangedListener)?.let { audio.removeOnModeChangedListener(it) }
        modeListener = null
    }

    private fun check() {
        val now = isCallMode(audio.mode)
        if (now == inCall) return
        inCall = now
        if (now) onCallStarted() else onCallEnded()
    }

    companion object {
        private const val POLL_MS = 1_000L

        /** Ringing, cellular call, VoIP call, call screening and redirected calls. */
        fun isCallMode(mode: Int): Boolean = when (mode) {
            AudioManager.MODE_RINGTONE,
            AudioManager.MODE_IN_CALL,
            AudioManager.MODE_IN_COMMUNICATION,
            MODE_CALL_SCREENING,
            MODE_CALL_REDIRECT,
            MODE_COMMUNICATION_REDIRECT -> true
            else -> false
        }

        // Literal values so the check compiles against every minSdk; names match AudioManager.
        private const val MODE_CALL_SCREENING = 4
        private const val MODE_CALL_REDIRECT = 5
        private const val MODE_COMMUNICATION_REDIRECT = 6
    }
}
