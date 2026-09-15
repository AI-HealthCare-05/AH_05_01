package com.tmtn.app.audio

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tmtn.app.R

enum class TmtnSound { Tap, Paper, Reward }

/** App-local preferences. Never changes device volume; music is opt-in and home-only. */
object TmtnAudio {
    private var context: Context? = null
    private var manager: AudioManager? = null
    private var pool: SoundPool? = null
    private val ids = mutableMapOf<TmtnSound, Int>()
    private val loaded = mutableSetOf<Int>()
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())
    private var foreground = false
    private var home = false
    private var focusGranted = false
    private var prepared = false
    private var lastEffect = 0L
    private var generation = 0
    private var volumeRamp = 0
    private val playedEvents = linkedSetOf<String>()
    var effectsEnabled by mutableStateOf(true)
        private set
    var ambienceEnabled by mutableStateOf(false)
        private set
    var ambiencePlaying by mutableStateOf(false)
        private set

    fun initialize(source: Context) {
        if (context != null) return
        context = source.applicationContext
        manager = source.getSystemService(AudioManager::class.java)
        val prefs = source.getSharedPreferences("tmtn_sound", Context.MODE_PRIVATE)
        effectsEnabled = prefs.getBoolean("effects", true)
        ambienceEnabled = prefs.getBoolean("ambience", false)
        ContextCompat.registerReceiver(source.applicationContext, object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) { stopMusic(); pool?.autoPause() }
                else updateMusic()
                if (manager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) pool?.autoPause()
            }
        }, IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION).apply { addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY) }, ContextCompat.RECEIVER_NOT_EXPORTED)
        pool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build().also { soundPool ->
            soundPool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded += id }
            ids[TmtnSound.Tap] = soundPool.load(source, R.raw.tmtn_tap, 1)
            ids[TmtnSound.Paper] = soundPool.load(source, R.raw.tmtn_paper, 1)
            ids[TmtnSound.Reward] = soundPool.load(source, R.raw.tmtn_reward, 1)
        }
    }

    fun setEffects(enabled: Boolean) {
        effectsEnabled = enabled
        context?.getSharedPreferences("tmtn_sound", Context.MODE_PRIVATE)?.edit()?.putBoolean("effects", enabled)?.apply()
        if (!enabled) pool?.autoPause()
    }

    fun setAmbience(enabled: Boolean) {
        ambienceEnabled = enabled
        context?.getSharedPreferences("tmtn_sound", Context.MODE_PRIVATE)?.edit()?.putBoolean("ambience", enabled)?.apply()
        updateMusic()
    }

    fun setForeground(value: Boolean) { foreground = value; if (!value) pool?.autoPause(); updateMusic() }
    fun setHomeVisible(value: Boolean) { home = value; updateMusic() }

    fun play(sound: TmtnSound, eventKey: String? = null) {
        if (eventKey != null && !playedEvents.add(eventKey)) return
        if (playedEvents.size > 160) playedEvents.remove(playedEvents.first())
        val audio = manager ?: return
        if (!effectsEnabled || !foreground || audio.ringerMode != AudioManager.RINGER_MODE_NORMAL ||
            audio.getStreamVolume(AudioManager.STREAM_SYSTEM) == 0 || (audio.isMusicActive && !ambiencePlaying)) return
        val now = SystemClock.elapsedRealtime()
        if (eventKey == null && now - lastEffect < 80) return
        val id = ids[sound]?.takeIf { it in loaded } ?: return
        lastEffect = now
        pool?.play(id, .32f, .32f, 1, 0, 1f)
    }

    private fun updateMusic() {
        val audio = manager ?: return
        if (!foreground || !home || !ambienceEnabled || audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
            stopMusic(); return
        }
        if (player != null || audio.isMusicActive || audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) return
        val source = context ?: return
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val token = ++generation
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes).setOnAudioFocusChangeListener(listener@{ change ->
                if (token != generation) return@listener
                when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> if (foreground && home && ambienceEnabled && audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                        focusGranted = true
                        if (prepared) {
                            player?.setVolume(0f, 0f)
                            player?.start()
                            ambiencePlaying = player != null
                            fadeMusicIn(token)
                        }
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        focusGranted = false
                        if (prepared) player?.pause()
                        pool?.autoPause()
                        ambiencePlaying = false
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        focusGranted = false; if (prepared) player?.pause(); ambiencePlaying = false
                        pool?.autoPause()
                    }
                    AudioManager.AUDIOFOCUS_LOSS -> { stopMusic(); pool?.autoPause() }
                }
            }, handler).build()
        if (audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        focus = request
        focusGranted = true
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(attributes)
                source.resources.openRawResourceFd(R.raw.tmtn_home).use { fd -> setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
                isLooping = true
                setVolume(0f, 0f)
                setOnErrorListener { _, _, _ -> stopMusic(); true }
                setOnPreparedListener { readyPlayer ->
                    if (token != generation) return@setOnPreparedListener
                    prepared = true
                    if (!foreground || !home || !ambienceEnabled || !focusGranted || audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return@setOnPreparedListener
                    readyPlayer.start()
                    ambiencePlaying = true
                    fadeMusicIn(token)
                }
                prepareAsync()
            }
        } catch (_: Exception) { stopMusic() }
    }

    /** The same quiet entrance is used after interruptions; stale ramps never resume playback. */
    private fun fadeMusicIn(token: Int) {
        val ramp = ++volumeRamp
        repeat(12) { tick -> handler.postDelayed({
            if (generation == token && volumeRamp == ramp && focusGranted && ambiencePlaying) {
                val level = (tick + 1) / 12f * .35f
                player?.setVolume(level, level)
            }
        }, (tick + 1) * 50L) }
    }

    private fun stopMusic() {
        generation++
        player?.release()
        player = null
        ambiencePlaying = false
        focusGranted = false
        prepared = false
        focus?.let { manager?.abandonAudioFocusRequest(it) }
        focus = null
    }
}
