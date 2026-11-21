package science.prokulski.apknewsreader

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.os.Build
import android.os.IBinder

/** Foreground podcast playback using Android platform APIs, no Media3 dependency. */
class PodcastService : Service() {
    private var player: MediaPlayer? = null
    private var session: MediaSession? = null
    private var currentUrl: String? = null
    private var title: String = "Podcast"
    private var paused = false

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL, "Podcast playback", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        session = MediaSession(this, "APK News Reader").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = resume()
                override fun onPause() = pause()
                override fun onStop() = stopSelf()
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            PLAY -> {
                val url = intent.getStringExtra(URL) ?: return START_NOT_STICKY
                title = intent.getStringExtra(TITLE) ?: "Podcast"
                if (url == currentUrl && paused) resume() else start(url)
            }
            PAUSE -> pause()
            RESUME -> resume()
            STOP -> { stopSelf(); return START_NOT_STICKY }
        }
        return START_NOT_STICKY
    }

    private fun start(url: String) {
        releasePlayer()
        currentUrl = url
        paused = false
        showNotification()
        player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            setOnPreparedListener { it.start(); showNotification() }
            setOnCompletionListener { stopSelf() }
            setOnErrorListener { _, _, _ -> stopSelf(); true }
            try { setDataSource(url); prepareAsync() } catch (_: Exception) { stopSelf() }
        }
    }

    private fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
        paused = true
        showNotification()
    }

    private fun resume() {
        player?.let { if (!it.isPlaying) runCatching { it.start() } }
        paused = false
        showNotification()
    }

    private fun showNotification() {
        val activity = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val toggleAction = if (paused) RESUME else PAUSE
        val toggle = PendingIntent.getService(this, 1,
            Intent(this, PodcastService::class.java).setAction(toggleAction),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 2,
            Intent(this, PodcastService::class.java).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(if (paused) "Paused" else "Playing podcast")
            .setContentIntent(activity)
            .setOngoing(!paused)
            .addAction(Notification.Action.Builder(null, if (paused) "Resume" else "Pause", toggle).build())
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .setStyle(Notification.MediaStyle().setMediaSession(session?.sessionToken))
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIFICATION_ID, notification)
    }

    private fun releasePlayer() {
        player?.runCatching { reset() }
        player?.release()
        player = null
    }

    override fun onDestroy() {
        releasePlayer()
        session?.release()
        session = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val PLAY = "podcast.play"
        const val PAUSE = "podcast.pause"
        const val RESUME = "podcast.resume"
        const val STOP = "podcast.stop"
        const val URL = "url"
        const val TITLE = "title"
        private const val CHANNEL = "podcast"
        private const val NOTIFICATION_ID = 1001
    }
}
