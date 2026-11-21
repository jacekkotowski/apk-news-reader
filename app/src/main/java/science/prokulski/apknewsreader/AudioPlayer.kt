package science.prokulski.apknewsreader

import android.media.MediaPlayer

/** Minimal podcast preview player; no background service yet. */
class AudioPlayer {
    private var player: MediaPlayer? = null
    fun play(url: String) {
        stop()
        player = MediaPlayer().apply {
            setDataSource(url)
            setOnPreparedListener { it.start() }
            setOnErrorListener { _, _, _ -> stop(); true }
            prepareAsync()
        }
    }
    fun stop() {
        player?.runCatching { stop() }
        player?.release()
        player = null
    }
}
