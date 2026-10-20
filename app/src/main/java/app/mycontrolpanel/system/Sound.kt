package app.mycontrolpanel.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.mycontrolpanel.MainActivity
import app.mycontrolpanel.R
import app.mycontrolpanel.core.Ambient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Calm sounds, generated on the device (no files, no network, no account) — plus looping a sound file
 * the user picked. Runs as a media foreground service so it keeps playing with the screen off,
 * and fades out gently when the sleep timer ends.
 */
class SoundService : Service() {

    data class State(val playing: Boolean = false, val label: String = "", val endsAt: Long? = null)

    companion object {
        private val _state = MutableStateFlow(State())
        val state: StateFlow<State> get() = _state

        private const val CHANNEL = "sounds"
        private const val ACTION_AMBIENT = "ambient"
        private const val ACTION_FILE = "file"
        private const val ACTION_STOP = "stop"

        fun playAmbient(ctx: Context, a: Ambient, timerMin: Int) = start(ctx, Intent(ctx, SoundService::class.java)
            .setAction(ACTION_AMBIENT).putExtra("kind", a.name).putExtra("timer", timerMin))

        fun playFile(ctx: Context, uri: String, name: String, timerMin: Int) = start(ctx, Intent(ctx, SoundService::class.java)
            .setAction(ACTION_FILE).putExtra("uri", uri).putExtra("name", name).putExtra("timer", timerMin))

        fun stop(ctx: Context) {
            if (_state.value.playing) runCatching { ctx.startService(Intent(ctx, SoundService::class.java).setAction(ACTION_STOP)) }
        }

        private fun start(ctx: Context, i: Intent) {
            runCatching { androidx.core.content.ContextCompat.startForegroundService(ctx, i) }
        }
    }

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var synth: Thread? = null
    @Volatile private var running = false
    @Volatile private var gain = 0f          // current volume 0..1 (fades)
    @Volatile private var target = 0f
    private var player: MediaPlayer? = null
    private val timerStop = Runnable { fadeOutAndStop() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { fadeOutAndStop(); return START_NOT_STICKY }
            ACTION_AMBIENT -> {
                val kind = Ambient.values().find { it.name == intent.getStringExtra("kind") } ?: Ambient.RAIN
                foreground("${kind.icon} ${kind.label}")
                stopAll()
                startSynth(kind)
                armTimer(intent.getIntExtra("timer", 0), "${kind.icon} ${kind.label}")
            }
            ACTION_FILE -> {
                val name = intent.getStringExtra("name") ?: "Your sound"
                foreground("🎵 $name")
                stopAll()
                if (!startFile(intent.getStringExtra("uri") ?: "")) { finish(); return START_NOT_STICKY }
                armTimer(intent.getIntExtra("timer", 0), "🎵 $name")
            }
            else -> finish()
        }
        return START_NOT_STICKY
    }

    private fun armTimer(min: Int, label: String) {
        main.removeCallbacks(timerStop)
        val ends = if (min > 0) System.currentTimeMillis() + min * 60_000L else null
        if (min > 0) main.postDelayed(timerStop, min * 60_000L)
        _state.value = State(true, label, ends)
        foreground(label, ends)
    }

    private fun foreground(label: String, endsAt: Long? = null) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Calm sounds", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, SoundService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val text = endsAt?.let { "Fades out at " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(it)) } ?: "Playing"
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_panel).setContentTitle(label).setContentText(text)
            .setContentIntent(open).setOngoing(true).setSilent(true)
            .addAction(0, "Stop", stop).build()
        ServiceCompat.startForeground(this, 7, n,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
    }

    // ---------------- generated sounds ----------------

    private fun startSynth(kind: Ambient) {
        running = true; gain = 0f; target = 0.9f
        synth = Thread({ synthLoop(kind) }, "calm-sound").also { it.start() }
    }

    private fun synthLoop(kind: Ambient) {
        val rate = 44100
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(maxOf(minBuf, rate)).setTransferMode(AudioTrack.MODE_STREAM).build()
        val gen = NoiseGen(kind, rate)
        val buf = ShortArray(2048)
        try {
            track.play()
            while (running) {
                for (i in buf.indices) {
                    gain += (target - gain) * 0.00003f                 // ~1–2 s glide for fades
                    buf[i] = (gen.next() * gain * 26000f).toInt().coerceIn(-32767, 32767).toShort()
                }
                track.write(buf, 0, buf.size)
                if (target == 0f && gain < 0.002f) running = false
            }
        } catch (_: Exception) {
        } finally {
            runCatching { track.stop() }; track.release()
            if (target == 0f) main.post { finish() }
        }
    }

    /** Gentle procedural sounds. All output roughly in -1..1. */
    private class NoiseGen(val kind: Ambient, val rate: Int) {
        private val rnd = Random(System.nanoTime())
        private var brown = 0f
        private var b0 = 0f; private var b1 = 0f; private var b2 = 0f   // pink filter state
        private var lp = 0f
        private var t = 0L
        private var drop = 0f
        fun white() = rnd.nextFloat() * 2f - 1f
        fun pink(): Float {
            val w = white()
            b0 = 0.99765f * b0 + w * 0.0990460f; b1 = 0.96300f * b1 + w * 0.2965164f; b2 = 0.57000f * b2 + w * 1.0526913f
            return (b0 + b1 + b2 + w * 0.1848f) * 0.18f
        }
        fun brownNoise(): Float { brown = (brown + white() * 0.02f) * 0.998f; return (brown * 3.2f).coerceIn(-1f, 1f) }
        fun next(): Float {
            t++
            val sec = t.toFloat() / rate
            return when (kind) {
                Ambient.WHITE -> white() * 0.35f
                Ambient.PINK -> pink()
                Ambient.BROWN -> brownNoise()
                Ambient.OCEAN -> {
                    // waves: brown noise swelling every ~9 s, plus a soft hiss on the crest
                    val swell = 0.5f + 0.5f * sin(2 * PI * sec / 9.0).toFloat()
                    brownNoise() * (0.25f + 0.75f * swell) + pink() * 0.25f * swell * swell
                }
                Ambient.FAN -> brownNoise() * 0.8f + sin(2 * PI * 58.0 * sec).toFloat() * 0.03f
                Ambient.RAIN -> {
                    // soft hiss + random droplets
                    lp += (pink() - lp) * 0.35f
                    if (rnd.nextFloat() < 0.0009f) drop = 0.6f + rnd.nextFloat() * 0.4f
                    drop *= 0.992f
                    lp * 0.9f + white() * drop * 0.35f
                }
            }
        }
    }

    // ---------------- user's sound file ----------------

    private fun startFile(uri: String): Boolean = try {
        player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            setDataSource(this@SoundService, Uri.parse(uri))
            isLooping = true
            setVolume(0.9f, 0.9f)
            prepare(); start()
        }
        true
    } catch (e: Exception) { player?.release(); player = null; false }

    // ---------------- stopping ----------------

    private fun fadeOutAndStop() {
        main.removeCallbacks(timerStop)
        if (synth != null && running) { target = 0f; return }   // the synth thread finishes after the fade
        val p = player
        if (p != null) {
            var v = 0.9f
            val step = object : Runnable {
                override fun run() {
                    v -= 0.05f
                    if (v <= 0f) { finish(); return }
                    runCatching { p.setVolume(v, v) }; main.postDelayed(this, 150)
                }
            }
            main.post(step)
            return
        }
        finish()
    }

    private fun stopAll() {
        running = false; synth = null
        player?.let { runCatching { it.stop() }; it.release() }; player = null
    }

    private fun finish() {
        main.removeCallbacks(timerStop)
        stopAll()
        _state.value = State()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { stopAll(); _state.value = State(); super.onDestroy() }
}
